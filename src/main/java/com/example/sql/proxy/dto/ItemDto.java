package com.example.sql.proxy.dto;

import java.math.BigDecimal;

public record ItemDto(Integer id, String name, String sku, BigDecimal price, String description) {
}
