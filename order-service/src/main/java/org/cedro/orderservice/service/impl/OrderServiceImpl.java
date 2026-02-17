package org.cedro.orderservice.service.impl;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Order;
import org.cedro.orderservice.repository.OrderRepository;
import org.cedro.orderservice.service.OrderService;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends AbstractService<Order> implements OrderService {

    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    protected MongoRepository<Order, String> getRepository() {
        return orderRepository;
    }

}
