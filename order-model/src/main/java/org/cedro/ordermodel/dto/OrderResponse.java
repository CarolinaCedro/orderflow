package org.cedro.ordermodel.dto;

import org.cedro.ordermodel.model.ApprovalStatus;
import org.cedro.ordermodel.model.Metadata;
import org.cedro.ordermodel.model.OrderItem;
import org.cedro.ordermodel.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String customerName,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        ApprovalStatus approvalStatus,
        String approvedBy,
        LocalDateTime approvalDate,
        String erpOrderId,
        Metadata metadata
) {
}
