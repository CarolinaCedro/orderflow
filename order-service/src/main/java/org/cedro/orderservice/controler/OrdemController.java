package org.cedro.orderservice.controler;

import org.cedro.orderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orderflow/v1/order")
public class OrdemController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final OrderService orderService;

    public OrdemController(OrderService orderService) {
        this.orderService = orderService;
    }


    @PostMapping
    ResponseEntity<String> orderVenda(@RequestBody String venda) {
        kafkaTemplate.send("vendas-topico", venda);
        return ResponseEntity.ok(this.orderService.venda(venda));
    }

    @GetMapping("/ping")
    ResponseEntity<String> ping() {
        return ResponseEntity.ok("Pingou");
    }


}
