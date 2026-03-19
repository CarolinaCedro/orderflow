package org.cedro.ordermodel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotBlank(message = "category is required")
        String category,

        @NotNull(message = "price is required")
        @Positive(message = "price must be positive")
        BigDecimal price,

        @NotNull(message = "stockQuantity is required")
        @Min(value = 0, message = "stockQuantity cannot be negative")
        Integer stockQuantity,

        String brand,
        String imageUrl,
        String size,
        String color,
        String material,
        Double weight
) {
}
