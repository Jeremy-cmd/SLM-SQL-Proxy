package com.example.sql.proxy.dto;

import java.util.List;
import java.util.Map;

public record OrderRequest(Integer userId, List<OrderItem> items) {

    public record OrderItem(String sku, Integer quantity) {}
}
