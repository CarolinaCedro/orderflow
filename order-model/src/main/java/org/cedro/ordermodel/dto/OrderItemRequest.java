package org.cedro.ordermodel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderItemRequest(

        @NotBlank(message = "productId is required")
        String productId,

        @NotBlank(message = "productName is required")
        String productName,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        Integer quantity,

        @NotNull(message = "unitPrice is required")
        @Positive(message = "unitPrice must be positive")
        BigDecimal unitPrice
) {
}
