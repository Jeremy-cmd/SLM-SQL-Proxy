package com.example.sql.proxy.service;

import com.example.sql.proxy.Exception.EmptyRequestException;
import com.example.sql.proxy.dto.OrderRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProxyServiceTest {

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private JdbcClient jdbcClient;
    private Integer userId;

    @BeforeEach
    void setup() {

        jdbcClient.sql("SET REFERENTIAL_INTEGRITY FALSE").update();

        jdbcClient.sql("TRUNCATE TABLE order_items RESTART IDENTITY").update();
        jdbcClient.sql("TRUNCATE TABLE orders RESTART IDENTITY").update();
        jdbcClient.sql("TRUNCATE TABLE items RESTART IDENTITY").update();
        jdbcClient.sql("TRUNCATE TABLE users RESTART IDENTITY").update();

        jdbcClient.sql("SET REFERENTIAL_INTEGRITY TRUE").update();

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
        String response = proxyService.createOrder(userId, List.of(orderItem));

        assertNotNull(response);
        assertTrue(response.contains("Success: Order #"));
        assertTrue(response.contains("Total Amount: $4999.90"));

        Map<String, Object> orderRow = jdbcClient.sql("SELECT * FROM orders").query().singleRow();
        BigDecimal amount = (BigDecimal) orderRow.get("amount");
        assertEquals(new BigDecimal("4999.90"), amount);
    }

    @Test
    void createOrderFail() {

        OrderRequest.OrderItem orderItem = new OrderRequest.OrderItem("TA-DI-TA-BR-01", 5);
        EmptyRequestException emptyRequestException = assertThrows(EmptyRequestException.class, () -> proxyService.createOrder(null, List.of(orderItem)));
        assertEquals(emptyRequestException.getMessage(), "Empty User Id");
    }

}