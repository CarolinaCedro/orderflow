package org.cedro.orderservice.controller;

import com.cedro.orderrestservice.rest.controller.AbstractController;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Order;
import org.cedro.orderservice.service.impl.OrderServiceImpl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orderflow/v1/order")
public class OrderController extends AbstractController<Order> {

    private final OrderServiceImpl orderService;

    public OrderController(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @Override
    protected AbstractService<Order> getService() {
        return orderService;
    }
}

