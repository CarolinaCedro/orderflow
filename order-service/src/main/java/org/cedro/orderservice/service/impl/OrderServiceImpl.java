package org.cedro.orderservice.service.impl;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.dto.OrderItemRequest;
import org.cedro.ordermodel.dto.OrderRequest;
import org.cedro.ordermodel.dto.OrderResponse;
import org.cedro.ordermodel.model.*;
import org.cedro.orderservice.repository.OrderRepository;
import org.cedro.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderServiceImpl extends AbstractService<OrderRequest, OrderResponse, Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final String VENDAS_TOPICO = "vendas-topico";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderServiceImpl(OrderRepository orderRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public ResponseEntity<OrderResponse> save(OrderRequest request, String returnEntity) {
        Order order = toEntity(request);
        Order saved = orderRepository.save(order);
        kafkaTemplate.send(VENDAS_TOPICO, saved.getId(), saved.getId());
        log.info("Order {} saved and published to {}", saved.getId(), VENDAS_TOPICO);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    protected Order toEntity(OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setCustomerName(request.customerName());
        order.setItems(mapItems(request.items()));
        order.setTotalAmount(request.totalAmount());
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setApprovalStatus(ApprovalStatus.PENDING);
        return order;
    }

    @Override
    protected Order updateEntity(Order existing, OrderRequest request) {
        existing.setCustomerId(request.customerId());
        existing.setCustomerName(request.customerName());
        existing.setItems(mapItems(request.items()));
        existing.setTotalAmount(request.totalAmount());
        return existing;
    }

    @Override
    protected OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getItems(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getApprovalStatus(),
                order.getApprovedBy(),
                order.getApprovalDate(),
                order.getErpOrderId(),
                order.getMetadata()
        );
    }

    @Override
    protected MongoRepository<Order, String> getRepository() {
        return orderRepository;
    }

    @Override
    protected Class<Order> getEntityClass() {
        return Order.class;
    }

    private List<OrderItem> mapItems(List<OrderItemRequest> requests) {
        return requests.stream().map(req -> {
            OrderItem item = new OrderItem();
            item.setProductId(req.productId());
            item.setProductName(req.productName());
            item.setQuantity(req.quantity());
            item.setUnitPrice(req.unitPrice());
            item.setTotalPrice(req.unitPrice().multiply(BigDecimal.valueOf(req.quantity())));
            return item;
        }).toList();
    }
}
