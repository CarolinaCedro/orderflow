package org.cedro.notificationservice.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @RabbitListener(queues = "paymentQueue")
    public void listenPaymentStatus(String status) {
        System.out.println("Received payment status: " + status);
        // Lógica para enviar notificações, por exemplo, via e-mail
    }
}

