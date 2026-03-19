package org.cedro.orderservice.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cedro.ordermodel.model.ApprovalStatus;
import org.cedro.ordermodel.model.OrderStatus;
import org.cedro.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentListener.class);

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderPaymentListener(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @KafkaListener(topics = "payment-processed", containerFactory = "orderPaymentListenerContainerFactory")
    public void onPaymentProcessed(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();

            orderRepository.findById(orderId).ifPresentOrElse(order -> {
                if ("PAYMENT_SUCCESS".equals(status)) {
                    order.setStatus(OrderStatus.COMPLETED);
                    order.setApprovalStatus(ApprovalStatus.APPROVED);
                    order.setApprovedBy("payment-service");
                    order.setApprovalDate(LocalDateTime.now());
                    log.info("Order {} completed successfully", orderId);
                } else {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setApprovalStatus(ApprovalStatus.REJECTED);
                    log.warn("Order {} cancelled due to payment failure: {}", orderId, status);
                }
                orderRepository.save(order);
            }, () -> log.error("Order {} not found for payment update", orderId));

        } catch (Exception e) {
            log.error("Failed to process payment event. Payload: {}", payload, e);
        }
    }
}
