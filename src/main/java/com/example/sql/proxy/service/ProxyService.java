package com.example.sql.proxy.service;

import com.example.sql.proxy.dto.ItemDto;
import com.example.sql.proxy.dto.OrderRequest;
import com.example.sql.proxy.model.Item;
import com.example.sql.proxy.repository.ItemRepository;
import com.example.sql.proxy.validator.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final JdbcClient jdbcClient;
    private final ChatClient client;
    private final SqlValidator sqlValidator;

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

    @Tool(name = "create new order", description = "creates a new order with items")
    @Transactional
    public String createOrder(OrderRequest orderRequest) {
        return "";
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

    private String getDatabaseSchema() {

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
}
