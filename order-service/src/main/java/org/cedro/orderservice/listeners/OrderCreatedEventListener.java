package org.cedro.orderservice.listeners;

import org.cedro.orderservice.events.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventListener implements ApplicationListener<OrderCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    @Override
    public void onApplicationEvent(OrderCreatedEvent event) {
        log.info("Order created with ID: {}", event.getOrderId());
    }

    @Override
    public boolean supportsAsyncExecution() {
        return ApplicationListener.super.supportsAsyncExecution();
    }
}

