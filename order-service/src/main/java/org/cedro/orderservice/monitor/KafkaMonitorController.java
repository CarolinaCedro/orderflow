package org.cedro.orderservice.monitor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orderflow/v1/kafka")
public class KafkaMonitorController {

    private final KafkaEventStore store;

    public KafkaMonitorController(KafkaEventStore store) {
        this.store = store;
    }

    @GetMapping("/events")
    public ResponseEntity<List<KafkaMonitorEvent>> events(
            @RequestParam(required = false) String topic
    ) {
        var all = store.getEvents();
        if (topic != null) {
            all = all.stream().filter(e -> e.topic().equals(topic)).toList();
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/topics")
    public ResponseEntity<Map<String, Long>> topicCounts() {
        var counts = store.getEvents().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        KafkaMonitorEvent::topic,
                        java.util.stream.Collectors.counting()
                ));
        return ResponseEntity.ok(counts);
    }

    @DeleteMapping("/events")
    public ResponseEntity<Void> clearEvents() {
        store.clear();
        return ResponseEntity.noContent().build();
    }
}
