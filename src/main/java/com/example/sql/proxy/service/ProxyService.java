package com.example.sql.proxy.service;

import com.example.sql.proxy.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyService {

    private final UserRepository userRepository;
    private final JdbcClient jdbcClient;
    private final ChatClient client;
    private final SqlValidator sqlValidator;
    private User lastCreatedUser;
    private boolean tool;

    @Tool(description = "This saves a user to the database")
    public UserDto addUser(UserDto userDto) {

        this.tool = true;

        User user = new User();
        user.setFirstName(userDto.firstName());
        user.setLastName(userDto.lastName());
        user.setAddress(userDto.address());

        lastCreatedUser = userRepository.save(user);

        return new UserDto(user.getId(), user.getFirstName(), user.getLastName(), user.getAddress());


    }

    public List<UserDto> getAllUsers() {

        return userRepository.findAll().stream()
                .map(user -> new UserDto(user.getId(), user.getFirstName(),
                        user.getLastName(), user.getAddress())).toList();

    }

    public List<User> generate(String message) {

        this.tool = false;

        lastCreatedUser = null;

        String currentSchema = getDatabaseSchema();

        String query = client.prompt()
                .system(s -> s.param("schema", currentSchema))
                .user(message)
                .tools(this)
                .call()
                .content();

        if (this.tool) {
            log.info("user added successfully");
            return List.of(lastCreatedUser);
        }

        sqlValidator.validateSQLQuery(query);

        return jdbcClient.sql(query).query(User.class).list();

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
