package org.cedro.orderservice.monitor;

import java.time.Instant;

public record KafkaMonitorEvent(
        String topic,
        String key,
        String payload,
        int partition,
        long offset,
        Instant receivedAt
) {}
