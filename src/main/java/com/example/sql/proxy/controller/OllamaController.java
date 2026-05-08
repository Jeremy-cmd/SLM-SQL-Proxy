package com.example.sql.proxy.controller;

import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class OllamaController {

    private final ChatClient client;
    private final ProxyService proxyService;

    @GetMapping
    public String generate(@RequestParam("message") String message) {

        return client.prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/users")
    public List<UserDto> getUsers() {
        return proxyService.getAllUsers();
    }

    @PostMapping("/add")
    public UserDto addUser(@RequestBody UserDto userDto) {
        return proxyService.addUser(userDto);

    }
}
