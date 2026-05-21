package com.example.sql.proxy.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "order_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_order_item", columnNames = {"order_id", "item_id"})
        }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer orderId;
    private Integer itemId;
    private Integer quantity = 1;
}
