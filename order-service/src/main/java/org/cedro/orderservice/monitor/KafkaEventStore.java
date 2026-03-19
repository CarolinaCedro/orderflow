package org.cedro.orderservice.monitor;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
public class KafkaEventStore {

    private static final int MAX_EVENTS = 100;

    private final Deque<KafkaMonitorEvent> events = new ArrayDeque<>();

    @KafkaListener(
            topics = {"vendas-topico", "payment-processed"},
            containerFactory = "monitorKafkaListenerContainerFactory"
    )
    public void capture(ConsumerRecord<String, String> record) {
        var event = new KafkaMonitorEvent(
                record.topic(),
                record.key(),
                record.value(),
                record.partition(),
                record.offset(),
                Instant.ofEpochMilli(record.timestamp())
        );
        synchronized (events) {
            events.addFirst(event);
            if (events.size() > MAX_EVENTS) {
                events.removeLast();
            }
        }
    }

    public List<KafkaMonitorEvent> getEvents() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public void clear() {
        synchronized (events) {
            events.clear();
        }
    }
}
