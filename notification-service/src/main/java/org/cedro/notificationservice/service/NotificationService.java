package org.cedro.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();
            String processedAt = node.path("processedAt").asText();

            if ("PAYMENT_SUCCESS".equals(status)) {
                log.info("[NOTIFICATION] Order {} approved at {}. Sending confirmation to customer.", orderId, processedAt);
                sendConfirmationEmail(orderId);
            } else {
                log.warn("[NOTIFICATION] Order {} payment failed (status={}). Sending failure alert to customer.", orderId, status);
                sendFailureAlert(orderId, status);
            }
        } catch (Exception e) {
            log.error("Failed to process payment notification. Payload: {}", payload, e);
        }
    }

    private void sendConfirmationEmail(String orderId) {
        // Simulação — em produção: JavaMailSender, SendGrid, AWS SES, etc.
        log.info("[EMAIL] To: customer | Subject: Order {} confirmed | Body: Your payment was approved!", orderId);
    }

    private void sendFailureAlert(String orderId, String status) {
        // Simulação — em produção: notificação push, SMS, etc.
        log.warn("[EMAIL] To: customer | Subject: Order {} payment issue | Body: Status={}", orderId, status);
    }
}

