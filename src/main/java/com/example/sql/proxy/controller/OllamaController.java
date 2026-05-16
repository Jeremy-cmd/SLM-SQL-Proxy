package com.example.sql.proxy.controller;

import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class OllamaController {

    private final ProxyService proxyService;

    @GetMapping("/users")
    public List<UserDto> getUsers() {
        return proxyService.getAllUsers();
    }

    @PostMapping("/generate")
    public  List<UserDto> generate(@RequestParam("message") String message) {
        return proxyService.generate(message);
    }

    @PostMapping("/add")
    public UserDto addUser(@RequestBody UserDto userDto) {
        return proxyService.addUser(userDto);

    }
}
