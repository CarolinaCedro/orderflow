package org.cedro.ordermodel.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.cedro.ordermodel.model.Order.ORDERS_COLLECTION;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = ORDERS_COLLECTION)
public class Order {

    public static final String ORDERS_COLLECTION = "orders";

    @Id
    private String id;

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "customerName is required")
    private String customerName;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItem> items;

    @NotNull(message = "totalAmount is required")
    @Positive(message = "totalAmount must be positive")
    private BigDecimal totalAmount;

    private OrderStatus status;

    private ApprovalStatus approvalStatus;

    private String approvedBy;
    private LocalDateTime approvalDate;

    private String erpOrderId;

    private Metadata metadata;
}
