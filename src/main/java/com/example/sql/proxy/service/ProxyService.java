package com.example.sql.proxy.service;

import com.example.sql.proxy.SqlValidator;
import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final UserRepository userRepository;
    private final JdbcClient jdbcClient;
    private final ChatClient client;
    private final SqlValidator sqlValidator;

    public UserDto addUser(UserDto userDto) {

        User user = new User();
        user.setFirstName(userDto.firstName());
        user.setLastName(userDto.lastName());
        user.setAddress(userDto.address());

        userRepository.save(user);

        return new UserDto(user.getId(), user.getFirstName(), user.getLastName(), user.getAddress());


    }

    public List<UserDto> getAllUsers() {

        return userRepository.findAll().stream()
                .map(user -> new UserDto(user.getId(), user.getFirstName(),
                        user.getLastName(), user.getAddress())).toList();

    }

    public List<User> generate(String message) {

        String currentSchema = getDatabaseSchema();

        String query = client.prompt()
                .system(s -> s.param("schema", currentSchema))
                .user(message)
                .call()
                .content();


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
