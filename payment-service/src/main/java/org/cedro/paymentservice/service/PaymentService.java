package org.cedro.paymentservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PAYMENT_PROCESSED_TOPIC = "payment-processed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "vendas-topico", groupId = "payment-group")
    public void processarVenda(String orderId) {
        log.info("Order received for payment processing: orderId={}", orderId);

        String status = simulatePayment(orderId);
        String payload = String.format(
                "{\"orderId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}",
                orderId, status, java.time.Instant.now()
        );

        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, payload);
        log.info("Payment processed: orderId={}, status={}", orderId, status);
    }

    private String simulatePayment(String orderId) {
        // Simula aprovação — em produção aqui entraria a lógica real (antifraude, gateway, etc.)
        log.info("Processing payment for order {}...", orderId);
        return "PAYMENT_SUCCESS";
    }
}
