package com.example.sql.proxy.repository;

import com.example.sql.proxy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
