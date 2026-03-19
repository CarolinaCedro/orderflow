package org.cedro.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @KafkaListener(topics = "payment-processed", groupId = "notification-group")
    public void listenPaymentStatus(String status) {
        log.info("Payment status received: {}", status);
    }
}

