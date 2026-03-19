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
        log.info("Order received for payment processing: {}", orderId);
        // TODO: implementar lógica de pagamento
        String status = "PAYMENT_SUCCESS";
        kafkaTemplate.send(PAYMENT_PROCESSED_TOPIC, orderId, status);
        log.info("Payment result published to {}: orderId={}, status={}", PAYMENT_PROCESSED_TOPIC, orderId, status);
    }
}
