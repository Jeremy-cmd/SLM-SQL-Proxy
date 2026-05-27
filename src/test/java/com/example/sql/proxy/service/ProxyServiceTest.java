package com.example.sql.proxy.service;

import com.example.sql.proxy.dto.OrderRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@RequiredArgsConstructor
@Transactional
class ProxyServiceTest {

    private final ProxyService proxyService;
    private final JdbcClient jdbcClient;
    private Integer userId;

    @BeforeEach
    void setup() {
        jdbcClient.sql("TRUNCATE TABLE order_items, orders, items, users RESTART IDENTITY").update();
        jdbcClient.sql("INSERT INTO users (first_name, last_name, address) VALUES ('first', 'last', '123 Main st')").update();

        userId = jdbcClient.sql("SELECT id FROM users LIMIT 1").query(Integer.class).single();

        jdbcClient.sql("""
                    INSERT INTO items (name, sku, price, description)
                    VALUES ('Dinner Table', 'TA-DI-TA-BR-01', 999.98, 'Large Dinner Table for 10 people')
                """).update();

    }

    @Test
    void createOrderSuccess() {

        OrderRequest.OrderItem orderItem = new OrderRequest.OrderItem("TA-DI-TA-BR-01", 5);

    }

}