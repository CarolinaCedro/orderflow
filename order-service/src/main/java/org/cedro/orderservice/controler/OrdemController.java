package org.cedro.orderservice.controler;

import com.cedro.orderrestservice.rest.controller.AbstractControllerJpa;
import com.cedro.orderrestservice.rest.service.impl.AbstractServiceJpa;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.cedro.ordermodel.domains.models.Order;
import org.cedro.orderservice.events.OrderCreatedEvent;
import org.cedro.orderservice.service.OrderService;
import org.cedro.orderservice.service.impl.OrderServiceImpl;
import org.cedro.orderutils.feign.viacep.record.Endereco;
import org.cedro.orderutils.feign.viacep.service.ViaCep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orderflow/v1/order")
public class OrdemController  {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ApplicationEventPublisher publisher;

    private final ViaCep viaCep;

    @Value("${custom.message}")
    private String mensagemTeste;


    private final OrderService orderService;

    public OrdemController(ViaCep viaCep, OrderService orderService) {
        this.viaCep = viaCep;
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

    //Apenas Teste com o feingh via cep
    @GetMapping("/endereco/{cep}")
    ResponseEntity<Endereco> getEndereco(@PathVariable String cep) {
        Endereco endereco = viaCep.getEndereco(cep);
        if (endereco != null) {
            return ResponseEntity.ok(endereco);
        }
        throw new RuntimeException("Endereço não encontrado para o CEP: " + cep);

    }

    @GetMapping("/publicando")
    ResponseEntity<String> eventPublicTest() {
        String id = "1";
        publisher.publishEvent(new OrderCreatedEvent(this, id));
        return ResponseEntity.ok("Evento OrderCreatedEvent publicado com sucesso!");
    }


    @GetMapping("/config-server-ping")
    public String getMensagemTeste() {
        return mensagemTeste;
    }


}
