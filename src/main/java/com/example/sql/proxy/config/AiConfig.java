package com.example.sql.proxy.config;

import com.example.sql.proxy.service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                        you are a bot that takes a message and converts\s
                        that message into an sql script for a postgresql database. the data you return must be ONLY an sql script
                                and if the message is not related to an sql script return a message saying wrong
                                message need a message that can be converted to an sql statement.
                                if theres WHO WHAT in te message those are select statements not inserts or updates.
                                If the user asks for a specific, single person or 'the' user,\s
                                            include 'LIMIT 1' in the SQL.
                                        
                              Current Database Schema:
                              {schema}
                                """)
                .build();
    }
}
