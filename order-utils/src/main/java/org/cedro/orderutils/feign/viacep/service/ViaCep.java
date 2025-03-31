package org.cedro.orderutils.feign.viacep.service;


import org.cedro.orderutils.feign.viacep.record.Endereco;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(url = "https://viacep.com.br/ws/", name = "viacep")
public interface ViaCep {

    @GetMapping("{cep}/json/")
    Endereco getEndereco(@PathVariable String cep);
}
