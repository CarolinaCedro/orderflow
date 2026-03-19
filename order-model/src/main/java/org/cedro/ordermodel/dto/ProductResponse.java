package org.cedro.ordermodel.dto;

import java.math.BigDecimal;

public record ProductResponse(
        String id,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stockQuantity,
        Boolean isActive,
        String brand,
        String imageUrl,
        String size,
        String color,
        String material,
        Double weight,
        Double rating,
        Integer reviewCount
) {
}
