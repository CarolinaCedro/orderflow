package org.cedro.orderservice.listeners;

import org.cedro.ordermodel.model.Metadata;
import org.cedro.ordermodel.model.Order;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderMetadataListener extends AbstractMongoEventListener<Order> {

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Order> event) {
        Order order = event.getSource();

        if (order.getMetadata() == null) {
            order.setMetadata(new Metadata());
        }

        Metadata metadata = order.getMetadata();

        if (metadata.getCreatedAt() == null) {
            metadata.setCreatedAt(LocalDateTime.now());
        }

        metadata.setUpdatedAt(LocalDateTime.now());
    }
}
