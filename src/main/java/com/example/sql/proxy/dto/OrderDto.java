package com.example.sql.proxy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderDto(Integer id, Integer userId, BigDecimal amount, OffsetDateTime orderDate) {
}
