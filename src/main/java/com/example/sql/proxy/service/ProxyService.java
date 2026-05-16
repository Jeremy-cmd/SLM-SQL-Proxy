package com.example.sql.proxy.service;

import com.example.sql.proxy.validator.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
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
    private final JdbcClient jdbcClient;
    private final ChatClient client;
    private final SqlValidator sqlValidator;

    private static final ThreadLocal<UserDto> threadLocalUser = new ThreadLocal<>();


    @Tool(description = "Executes an INSERT operation to create a NEW user record. Triggered ONLY by verbs like 'add', 'create', or 'save'." +
            "\"NEVER call this for 'list', 'show', 'search', or 'who', or 'give', or 'send' queries.\"")
    public UserDto addUser(UserDto userDto) {

        User user = new User();
        user.setFirstName(userDto.firstName());
        user.setLastName(userDto.lastName());
        user.setAddress(userDto.address());

        User savedUser = userRepository.save(user);

        UserDto savedDtoUser = new UserDto(savedUser.getId(), savedUser.getFirstName(), savedUser.getLastName(), savedUser.getAddress());
        threadLocalUser.set(savedDtoUser);
        return savedDtoUser;

    }

    public List<UserDto> getAllUsers() {

        return userRepository.findAll().stream()
                .map(user -> new UserDto(user.getId(), user.getFirstName(),
                        user.getLastName(), user.getAddress())).toList();

    }

    public List<UserDto> generate(String message) {

        threadLocalUser.remove();

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

            UserDto retrievedUser = threadLocalUser.get();

            if (retrievedUser != null) {
                log.info("user added successfully");
                return List.of(retrievedUser);
            }


            sqlValidator.validateSQLQuery(query);

            return jdbcClient.sql(query).query(User.class)
                    .stream().map(user -> new UserDto(user.getId(), user.getFirstName(),
                            user.getLastName(), user.getAddress())).toList();

        }
        finally {
            threadLocalUser.remove();
        }

    }

    private String getDatabaseSchema() {

        String sql = """
                SELECT table_name,
                    string_agg(column_name || ' (' || data_type || ')', ', ') as columns
                FROM information_schema.columns
                WHERE table_schema = 'public'
                GROUP BY table_name;
                """;

        List<Map<String, Object>> rows = jdbcClient.sql(sql).query().listOfRows();

        StringBuilder schemaBuilder = new StringBuilder();
        for(Map<String, Object> row : rows) {
            schemaBuilder.append("Table: ").append(row.get("table_name"))
                         .append(" Columns: ").append(row.get("columns"))
                    .append("\n");
        }

        return schemaBuilder.toString();
    }
}
