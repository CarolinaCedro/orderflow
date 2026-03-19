package org.cedro.orderservice.service.impl;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Order;
import org.cedro.orderservice.repository.OrderRepository;
import org.cedro.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final String VENDAS_TOPICO = "vendas-topico";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderServiceImpl(OrderRepository orderRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Override
    public ResponseEntity<Order> save(Order order, String returnEntity) {
        Order saved = orderRepository.save(order);
        String orderId = saved.getId();
        kafkaTemplate.send(VENDAS_TOPICO, orderId, orderId);
        log.info("Order {} saved and published to {}", orderId, VENDAS_TOPICO);
        return ResponseEntity.ok(saved);
    }

    @Override
    protected MongoRepository<Order, String> getRepository() {
        return orderRepository;
    }
}
