package com.example.sql.proxy.dto;

import java.util.List;
import java.util.Map;

public record OrderRequest(Integer id, Integer userId, List<orderItem> items) {

    public record orderItem(String sku, Integer quantity) {}
}
