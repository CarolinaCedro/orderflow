package org.cedro.ordermodel.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.cedro.ordermodel.model.Order.ORDERS_COLLECTION;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = ORDERS_COLLECTION)
public class Order {

    public static final String ORDERS_COLLECTION = "orders";

    @Id
    private String id;

    private String customerId;
    private String customerName;

    private List<OrderItem> items;

    private BigDecimal totalAmount;

    private OrderStatus status;

    private ApprovalStatus approvalStatus;

    private String approvedBy;
    private LocalDateTime approvalDate;

    private String erpOrderId;

    private Metadata metadata;

}

