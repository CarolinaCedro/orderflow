package org.cedro.orderservice.controller;

import com.cedro.orderrestservice.rest.controller.AbstractController;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.ordermodel.dto.OrderRequest;
import org.cedro.ordermodel.dto.OrderResponse;
import org.cedro.orderservice.service.impl.OrderServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orderflow/v1/order")
public class OrderController extends AbstractController<OrderRequest, OrderResponse> {

    private final OrderServiceImpl orderService;

    public OrderController(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @Override
    protected AbstractService<OrderRequest, OrderResponse, ?> getService() {
        return orderService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER')")
    public ResponseEntity<OrderResponse> save(OrderRequest value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<OrderResponse> update(String id, OrderRequest model) {
        return super.update(id, model);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deleteById(String id) {
        super.deleteById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<OrderResponse> findById(String id) {
        return super.findById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<List<OrderResponse>> list(Map<String, String> allRequestParams) {
        return super.list(allRequestParams);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Page<OrderResponse>> listPage(Map<String, String> allRequestParams, Pageable pageable) {
        return super.listPage(allRequestParams, pageable);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return super.count(allRequestParams);
    }
}
