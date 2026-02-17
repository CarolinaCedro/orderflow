package org.cedro.orderservice.controler;

import com.cedro.orderrestservice.rest.controller.AbstractController;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.model.Order;
import org.cedro.orderservice.service.impl.OrderServiceImpl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orderflow/v1/order")
public class OrdemController extends AbstractController<Order> {


    private final OrderServiceImpl orderService;

    public OrdemController(OrderServiceImpl orderService1) {
        this.orderService = orderService1;
    }

    @Override
    protected AbstractService<Order> getService() {
        return orderService;
    }


}
