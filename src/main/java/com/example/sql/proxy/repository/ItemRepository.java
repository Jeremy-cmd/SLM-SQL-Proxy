package com.example.sql.proxy.repository;

import com.example.sql.proxy.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Integer> {
}
