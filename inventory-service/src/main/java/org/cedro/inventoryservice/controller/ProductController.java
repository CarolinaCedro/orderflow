package org.cedro.inventoryservice.controller;

import com.cedro.orderrestservice.rest.controller.AbstractController;
import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.inventoryservice.service.impl.ProductServiceImpl;
import org.cedro.ordermodel.dto.ProductRequest;
import org.cedro.ordermodel.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orderflow/v1/product")
public class ProductController extends AbstractController<ProductRequest, ProductResponse> {

    private final ProductServiceImpl productService;

    public ProductController(ProductServiceImpl productService) {
        this.productService = productService;
    }

    @Override
    protected AbstractService<ProductRequest, ProductResponse, ?> getService() {
        return productService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> save(ProductRequest value, String returnEntity) {
        return super.save(value, returnEntity);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> update(String id, ProductRequest model) {
        return super.update(id, model);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deleteById(String id) {
        super.deleteById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<ProductResponse> findById(String id) {
        return super.findById(id);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<List<ProductResponse>> list(Map<String, String> allRequestParams) {
        return super.list(allRequestParams);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Page<ProductResponse>> listPage(Map<String, String> allRequestParams, Pageable pageable) {
        return super.listPage(allRequestParams, pageable);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'BUYER', 'VIEWER')")
    public ResponseEntity<Long> count(Map<String, String> allRequestParams) {
        return super.count(allRequestParams);
    }
}
