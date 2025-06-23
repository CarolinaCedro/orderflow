package org.cedro.orderutils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.FeignClient;

@SpringBootApplication
@FeignClient
public class OrderUtilsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderUtilsApplication.class, args);
    }

}
