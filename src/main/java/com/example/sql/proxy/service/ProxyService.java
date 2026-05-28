package com.example.sql.proxy.service;

import com.example.sql.proxy.Exception.EmptyRequestException;
import com.example.sql.proxy.dto.ItemDto;
import com.example.sql.proxy.dto.OrderRequest;
import com.example.sql.proxy.model.Item;
import com.example.sql.proxy.model.Order;
import com.example.sql.proxy.model.OrderItem;
import com.example.sql.proxy.repository.ItemRepository;
import com.example.sql.proxy.validator.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private String cachedSchema;

    private static final ThreadLocal<Object> threadLocalResult = new ThreadLocal<>();


    @Tool(description = "Executes an INSERT operation to create a NEW user record. Triggered ONLY by verbs like 'add', 'create', or 'save'." +
            "\"NEVER call this for 'list', 'show', 'search', or 'who', or 'give', or 'send' queries.\"")
    public UserDto addUser(UserDto userDto) {

        User user = new User();
        user.setFirstName(userDto.firstName());
        user.setLastName(userDto.lastName());
        user.setAddress(userDto.address());

        User savedUser = userRepository.save(user);

        UserDto savedDtoUser = new UserDto(savedUser.getId(), savedUser.getFirstName(), savedUser.getLastName(), savedUser.getAddress());
        threadLocalResult.set(savedDtoUser);
        return savedDtoUser;

    }

    @Tool(description = "Executes an INSERT STATEMENT to create a new ITEM record")
    public ItemDto addItem(ItemDto itemDto) {
        Item item = new Item();
        item.setName(itemDto.name());
        item.setSku(itemDto.sku());
        item.setPrice(itemDto.price());
        item.setDescription(itemDto.description());

        Item savedItem = itemRepository.save(item);
        ItemDto savedDtoItem = new ItemDto(savedItem.getId(), savedItem.getName(), savedItem.getSku(), savedItem.getPrice(), savedItem.getDescription());
        threadLocalResult.set(savedDtoItem);
        return savedDtoItem;
    }

    @Tool(description = "creates a new order with items")
    @Transactional
    public String createOrder(
            @ToolParam(description = "The database ID of the user")
            Integer userId,
            @ToolParam(description = "The item or items")
            List<OrderRequest.OrderItem> orderItems) {


        log.info("the correct method was called !!!");
        log.info("the contents of the order request are: " + userId + " " + orderItems);

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

        log.info("reaches !!");

        Map<String, Integer> skuToIdMap = items.stream().collect(Collectors.toMap(ItemDto::sku, ItemDto::id));
        Map<String, BigDecimal> skuToPriceMap = items.stream().collect(Collectors.toMap(ItemDto::sku, ItemDto::price));

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

        log.info("reaches here too !!");

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

        threadLocalResult.set(response);

        return response;
    }

    private void checkNonEmpty(Integer userId, List<OrderRequest.OrderItem> orderItems) {
        if(userId == null) {
            throw new EmptyRequestException("Empty User Id");
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

        threadLocalResult.remove();

        try {
            String currentSchema = getDatabaseSchema();

            var prompt = client.prompt()
                    .system(s -> s.param("schema", currentSchema))
                    .user(message);

            String lowerMsg = message.toLowerCase();
            if (lowerMsg.contains("add") || lowerMsg.contains("save") || lowerMsg.contains("create")) {
                prompt.tools(this);
            }

            String query = prompt.call().content();

            Object result = threadLocalResult.get();

            if (result != null) {
                log.info("user added successfully");
                return List.of(result);
            }


            sqlValidator.validateSQLQuery(query);

            return jdbcClient.sql(query).query()
                    .listOfRows();

        }
        finally {
            threadLocalResult.remove();
        }

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
