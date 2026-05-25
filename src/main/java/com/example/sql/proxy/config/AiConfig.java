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
                        you are a PostgreSQL expert that takes a message and converts\s
                        that message into an sql script for a postgresql database. the data you return must be ONLY an sql script
                        ONLY if it is a select type of message like WHO, WHAT, list, etc.
                                and if the message is not related to an sql script return a message saying wrong
                                message need a message that can be converted to an sql statement.
                                
                                You have two ways to help:
                                           \s
                                            1. ACTION (Tool Call): If the user wants to ADD, CREATE, or SAVE a new user, you MUST call 'addUser'.
                                            2. INFORMATION (SQL Only): If the user wants to FIND, GET, LIST, or SEARCH (Who, What, How many), you MUST return a raw SQL SELECT statement.
                                            
                                if theres WHO WHAT in the message those are select statements not inserts or updates.
                                - if the user wants to add/save a new user you MUST call the addUser tool
                                - For any other request (WHO, WHAT, list, etc.), generate a standard SQL SELECT statement.
                                - When generating SQL:
                                                - Return ONLY the raw SQL string.\s
                                                - Do not use markdown blocks (```sql).
                                                - keywords like all, and users mean more than one
                                - If the request is unrelated to database operations, respond with: 'wrong message'.
                                
                         CRITICAL RULES:
                                  - The word 'user' OR 'users' in a search request refers to the 'users' table, NOT the 'addUser' tool.
                                  - ONLY CALL addUser tool method ONLY IF the message Has intent to add new data.
                                  - When calling tools, provide standard JSON arguments directly as primitive fields or arrays. NEVER wrap the tool arguments in quotes, markdown, or a single raw text string
                                        
                              Current Database Schema:
                              {schema}
                                """)
                .build();
    }
}
