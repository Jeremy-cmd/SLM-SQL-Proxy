package com.example.sql.proxy.service;

import com.example.sql.proxy.Exception.EmptyRequestException;
import com.example.sql.proxy.Exception.ItemNotFoundException;
import com.example.sql.proxy.dto.ItemDto;
import com.example.sql.proxy.dto.OrderRequest;
import com.example.sql.proxy.model.Item;
import com.example.sql.proxy.repository.ItemRepository;
import com.example.sql.proxy.validator.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ChatClient client;
    private final SqlValidator sqlValidator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private String cachedSchema;

    // Whole-word match so e.g. "address" no longer triggers "add".
    private static final Pattern WRITE_INTENT = Pattern.compile(
            "\\b(add|save|create|insert|register|order|buy|purchase|place)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String GENERATED_QUERY_TIMEOUT = "5s";


    @Tool(description = "Executes an INSERT operation to create a NEW user record. Triggered ONLY by verbs like 'add', 'create', or 'save'." +
            "\"NEVER call this for 'list', 'show', 'search', or 'who', or 'give', or 'send' queries.\"", returnDirect = true)
    public UserDto addUser(UserDto userDto) {

        User user = new User();
        user.setFirstName(userDto.firstName());
        user.setLastName(userDto.lastName());
        user.setAddress(userDto.address());

        User savedUser = userRepository.save(user);

        return new UserDto(savedUser.getId(), savedUser.getFirstName(), savedUser.getLastName(), savedUser.getAddress());
    }

    @Tool(description = "Executes an INSERT STATEMENT to create a new ITEM record", returnDirect = true)
    public ItemDto addItem(ItemDto itemDto) {
        Item item = new Item();
        item.setName(itemDto.name());
        item.setSku(itemDto.sku());
        item.setPrice(itemDto.price());
        item.setDescription(itemDto.description());

        Item savedItem = itemRepository.save(item);
        return new ItemDto(savedItem.getId(), savedItem.getName(), savedItem.getSku(), savedItem.getPrice(), savedItem.getDescription());
    }

    @Tool(description = "creates a new order with items", returnDirect = true)
    public String createOrder(
            @ToolParam(description = "The database ID of the user")
            Integer userId,
            @ToolParam(description = "The item or items")
            List<OrderRequest.OrderItem> orderItems) {

        // Programmatic transaction: the LLM invokes this via prompt.tools(this), which bypasses
        // the Spring proxy, so @Transactional would silently not apply.
        return transactionTemplate.execute(status -> doCreateOrder(userId, orderItems));
    }

    private String doCreateOrder(Integer userId, List<OrderRequest.OrderItem> orderItems) {

        log.info("createOrder called: userId={} items={}", userId, orderItems);

        checkNonEmpty(userId, orderItems);


        List<OrderRequest.OrderItem> removedDuplicates = orderItems.stream()
                .collect(Collectors.groupingBy(OrderRequest.OrderItem::sku, Collectors.summingInt(OrderRequest.OrderItem::quantity)
                ))
                .entrySet().stream()
                .map(e -> new OrderRequest.OrderItem(e.getKey(), e.getValue()))
                .toList();

        List<String> skus = removedDuplicates.stream()
                .map(OrderRequest.OrderItem::sku).toList();

        String query = "SELECT * FROM items WHERE sku IN (:skus)";

        List<ItemDto> items = jdbcClient.sql(query)
                .param("skus", skus).query(ItemDto.class).stream().toList();

        Map<String, Integer> skuToIdMap = items.stream().collect(Collectors.toMap(ItemDto::sku, ItemDto::id));
        Map<String, BigDecimal> skuToPriceMap = items.stream().collect(Collectors.toMap(ItemDto::sku, ItemDto::price));

        List<String> missingSkus = skus.stream()
                .filter(sku -> !skuToIdMap.containsKey(sku))
                .toList();
        if (!missingSkus.isEmpty()) {
            throw new ItemNotFoundException("Unknown SKUs: " + missingSkus);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for(OrderRequest.OrderItem item : removedDuplicates) {

            BigDecimal price = skuToPriceMap.get(item.sku());
            BigDecimal quantity = BigDecimal.valueOf(item.quantity());
            totalAmount = totalAmount.add(price.multiply(quantity));

        }

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcClient.sql("""
                INSERT INTO orders (user_id, amount)
                VALUES (:userId, :amount)
                """)
                .param("userId", userId)
                .param("amount", totalAmount)
                .update(keyHolder, "id");

        Number generatedId = keyHolder.getKey();
        if(generatedId == null) {
            throw new IllegalArgumentException("Failed to retrieve generated order id");
        }

        int orderId = generatedId.intValue();

        Map[] batchItems = removedDuplicates.stream()
                .map(item -> {
                    int itemId = skuToIdMap.get(item.sku());
                    Map<String, Object> params = new HashMap<>();
                    params.put("orderId", orderId);
                    params.put("itemId", itemId);
                    params.put("quantity", item.quantity());
                    return params;
                }).toArray(Map[]::new);

        String batchSQL = """
                    INSERT INTO order_items (order_id, item_id, quantity)
                    VALUES (:orderId, :itemId, :quantity)
                    """;
        namedParameterJdbcTemplate.batchUpdate(batchSQL, batchItems);

        String response = String.format("Success: Order #%d created for user #%d. Total Amount: $%s",
                orderId, userId, totalAmount.toPlainString());

        return response;
    }

    private void checkNonEmpty(Integer userId, List<OrderRequest.OrderItem> orderItems) {
        if(userId == null) {
            throw new EmptyRequestException("Empty User Id");
        }

        if(orderItems == null || orderItems.isEmpty()) {
            throw new EmptyRequestException("Empty order items");
        }

        for(OrderRequest.OrderItem item : orderItems) {

            if(item.sku() == null) {
                throw new EmptyRequestException("Empty sku");
            }
            if(item.quantity() == null) {
                throw new EmptyRequestException("Empty quantity");
            }
        }

    }

    public List<UserDto> getAllUsers() {

        return userRepository.findAll().stream()
                .map(user -> new UserDto(user.getId(), user.getFirstName(),
                        user.getLastName(), user.getAddress())).toList();

    }

    public Object generate(String message) {

            String currentSchema = getDatabaseSchema();

            var prompt = client.prompt()
                    .system(s -> s.param("schema", currentSchema))
                    .user(message);

            boolean toolsEnabled = WRITE_INTENT.matcher(message).find();
            if (toolsEnabled) {
                prompt.tools(this);
            }

            String result = prompt.call().content();

            if (toolsEnabled) {
                try {
                    return List.of(objectMapper.readTree(result));
                } catch (JacksonException e) {

                }
            }

            sqlValidator.validateSQLQuery(result);
            return executeReadOnly(result);

    }

    /**
     * Runs LLM-generated SQL in a READ ONLY transaction with a statement timeout, so the
     * database itself rejects writes (e.g. data-modifying CTEs that pass the SELECT check)
     * and long-running queries such as pg_sleep or huge cross joins.
     */
    private List<Map<String, Object>> executeReadOnly(String query) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionTemplate.getTransactionManager());
        readOnly.setReadOnly(true);

        return readOnly.execute(status -> {
            jdbcClient.sql("SET TRANSACTION READ ONLY").update();
            jdbcClient.sql("SET LOCAL statement_timeout = '" + GENERATED_QUERY_TIMEOUT + "'").update();
            return jdbcClient.sql(query).query().listOfRows();
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSchemaStartup() {
        this.cachedSchema = fetchDBSchema();
    }

    private String fetchDBSchema() {
        String sql = """
                SELECT\s
                                t.table_name,
                                (SELECT string_agg(c.column_name || ' (' || c.data_type || ')', ', ')
                                 FROM information_schema.columns c
                                 WHERE c.table_name = t.table_name AND c.table_schema = 'public'
                                ) AS columns,
                                COALESCE(
                                    (SELECT string_agg(kcu.column_name || ' references ' || ccu.table_name || '(' || ccu.column_name || ')', ' | ')
                                     FROM information_schema.table_constraints tc
                                     JOIN information_schema.key_column_usage kcu\s
                                       ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                                     JOIN information_schema.constraint_column_usage ccu\s
                                       ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                                     WHERE tc.constraint_type = 'FOREIGN KEY'\s
                                       AND tc.table_name = t.table_name\s
                                       AND tc.table_schema = 'public'
                                    ), 'No Relationships'
                                ) AS relationships
                            FROM information_schema.tables t
                            WHERE t.table_schema = 'public'
                            GROUP BY t.table_name;
                """;

        List<Map<String, Object>> rows = jdbcClient.sql(sql).query().listOfRows();

        StringBuilder schemaBuilder = new StringBuilder();
        for(Map<String, Object> row : rows) {
            schemaBuilder.append("Table: ").append(row.get("table_name")).append("\n")
                    .append(" Columns: ").append(row.get("columns")).append("\n")
                    .append(" Relationships ").append(row.get("relationships"))
                    .append("\n");
        }

        return schemaBuilder.toString();
    }

    private String getDatabaseSchema() {
        return this.cachedSchema;
    }


}
