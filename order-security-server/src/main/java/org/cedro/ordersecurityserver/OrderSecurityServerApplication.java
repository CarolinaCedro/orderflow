package org.cedro.ordersecurityserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class OrderSecurityServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderSecurityServerApplication.class, args);
    }

}
