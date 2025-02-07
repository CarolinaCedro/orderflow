package org.cedro.paymentservice.service;


import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
public class PaymenteService {

    @KafkaListener(topics = "vendas-topico", groupId = "order-service-group")
    public void processarVenda(String msg) {
        System.out.println("Venda recebida: " + msg);
    }


}
