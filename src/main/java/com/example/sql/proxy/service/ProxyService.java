package com.example.sql.proxy.service;

import com.example.sql.proxy.dto.UserDto;
import com.example.sql.proxy.model.User;
import com.example.sql.proxy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final UserRepository userRepository;

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
}
