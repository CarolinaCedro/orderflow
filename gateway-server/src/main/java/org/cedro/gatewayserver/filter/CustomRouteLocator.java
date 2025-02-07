package org.cedro.gatewayserver.filter;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class CustomRouteLocator {

//    @Bean
//    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
//        return builder.routes()
//                // Rotas para Order Service
//                .route("order-service-post", r -> r.path("/order/**")
//                        .and().method("POST")
//                        .and().readBody(Order.class, s -> true)
//                        .filters(f -> f.filters(requestFilter, authFilter))
//                        .uri("http://localhost:8081"))
//                .route("order-service-get", r -> r.path("/order/**")
//                        .and().method("GET")
//                        .filters(f -> f.filters(authFilter))
//                        .uri("http://localhost:8081"))
//
//                // Rotas para Notification Service
//                .route("notification-service-post", r -> r.path("/notification/**")
//                        .and().method("POST")
//                        .and().readBody(Notification.class, s -> true)
//                        .filters(f -> f.filters(requestFilter, authFilter))
//                        .uri("http://localhost:8083"))
//                .route("notification-service-get", r -> r.path("/notification/**")
//                        .and().method("GET")
//                        .filters(f -> f.filters(authFilter))
//                        .uri("http://localhost:8083"))
//
//                // Rotas para Payment Service
//                .route("payment-service-post", r -> r.path("/payment/**")
//                        .and().method("POST")
//                        .and().readBody(Payment.class, s -> true)
//                        .filters(f -> f.filters(requestFilter, authFilter))
//                        .uri("http://localhost:8085"))
//                .route("payment-service-get", r -> r.path("/payment/**")
//                        .and().method("GET")
//                        .filters(f -> f.filters(authFilter))
//                        .uri("http://localhost:8085"))
//
//                // Rota para o Auth Server
//                .route("auth-server", r -> r.path("/login/**")
//                        .uri("http://localhost:8088"))
//
//                .build();
//    }
}
