package org.cedro.orderservice.service.impl;

import org.cedro.orderservice.service.OrderService;
import org.springframework.stereotype.Service;


@Service
public class OrderServiceImpl implements OrderService {
    @Override
    public String venda(String nome) {
        return "Venda registrada com sucesso!";
    }
}
