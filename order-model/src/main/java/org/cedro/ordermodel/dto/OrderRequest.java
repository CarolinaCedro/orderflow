package org.cedro.ordermodel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(

        @NotBlank(message = "customerId is required")
        String customerId,

        @NotBlank(message = "customerName is required")
        String customerName,

        @NotEmpty(message = "Order must have at least one item")
        @Valid
        List<OrderItemRequest> items,

        @NotNull(message = "totalAmount is required")
        @Positive(message = "totalAmount must be positive")
        BigDecimal totalAmount
) {
}
