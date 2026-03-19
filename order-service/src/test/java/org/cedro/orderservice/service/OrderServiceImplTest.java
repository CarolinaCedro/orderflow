package org.cedro.orderservice.service;

import org.cedro.ordermodel.dto.OrderItemRequest;
import org.cedro.ordermodel.dto.OrderRequest;
import org.cedro.ordermodel.dto.OrderResponse;
import org.cedro.ordermodel.model.ApprovalStatus;
import org.cedro.ordermodel.model.Order;
import org.cedro.ordermodel.model.OrderStatus;
import org.cedro.orderservice.repository.OrderRepository;
import org.cedro.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "mongoTemplate", mongoTemplate);
    }

    private OrderRequest buildRequest() {
        OrderItemRequest item = new OrderItemRequest("prod-1", "Product A", 2, new BigDecimal("50.00"));
        return new OrderRequest("cust-1", "Customer One", List.of(item), new BigDecimal("100.00"));
    }

    private Order buildSavedOrder() {
        Order order = new Order();
        order.setId("order-123");
        order.setCustomerId("cust-1");
        order.setCustomerName("Customer One");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setApprovalStatus(ApprovalStatus.PENDING);
        return order;
    }

    @Test
    void save_shouldPersistOrderAndPublishToKafka() {
        Order saved = buildSavedOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        ResponseEntity<OrderResponse> response = orderService.save(buildRequest(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("order-123");
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING_APPROVAL);

        verify(orderRepository).save(any(Order.class));
        verify(kafkaTemplate).send(eq("vendas-topico"), eq("order-123"), eq("order-123"));
    }

    @Test
    void findById_whenExists_shouldReturnOrder() {
        Order order = buildSavedOrder();
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(order));

        ResponseEntity<OrderResponse> response = orderService.findById("order-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo("order-123");
    }

    @Test
    void findById_whenNotFound_shouldReturn404() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<OrderResponse> response = orderService.findById("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_whenNotFound_shouldReturn404() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<OrderResponse> response = orderService.update("missing", buildRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void update_whenExists_shouldReturnUpdatedOrder() {
        Order existing = buildSavedOrder();
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(existing);

        ResponseEntity<OrderResponse> response = orderService.update("order-123", buildRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orderRepository).save(existing);
    }
}
