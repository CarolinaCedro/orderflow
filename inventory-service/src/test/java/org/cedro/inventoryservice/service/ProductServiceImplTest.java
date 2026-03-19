package org.cedro.inventoryservice.service;

import org.cedro.inventoryservice.repository.ProductRepository;
import org.cedro.inventoryservice.service.impl.ProductServiceImpl;
import org.cedro.ordermodel.dto.ProductRequest;
import org.cedro.ordermodel.dto.ProductResponse;
import org.cedro.ordermodel.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "mongoTemplate", mongoTemplate);
    }

    private ProductRequest buildRequest() {
        return new ProductRequest(
                "Camiseta Polo",
                "Camiseta polo masculina",
                "Vestuário",
                new BigDecimal("89.90"),
                100,
                "Lacoste",
                "https://img.example.com/polo.jpg",
                "M",
                "Branca",
                "Algodão",
                0.3
        );
    }

    private Product buildSavedProduct() {
        Product p = new Product();
        p.setId("prod-123");
        p.setName("Camiseta Polo");
        p.setCategory("Vestuário");
        p.setPrice(new BigDecimal("89.90"));
        p.setStockQuantity(100);
        p.setIsActive(true);
        return p;
    }

    @Test
    void save_shouldPersistAndReturnProductResponse() {
        Product saved = buildSavedProduct();
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ResponseEntity<ProductResponse> response = productService.save(buildRequest(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("prod-123");
        assertThat(response.getBody().name()).isEqualTo("Camiseta Polo");
        assertThat(response.getBody().isActive()).isTrue();

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void findById_whenExists_shouldReturnProduct() {
        Product product = buildSavedProduct();
        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        ResponseEntity<ProductResponse> response = productService.findById("prod-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo("prod-123");
    }

    @Test
    void findById_whenNotFound_shouldReturn404() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<ProductResponse> response = productService.findById("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_whenNotFound_shouldReturn404() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseEntity<ProductResponse> response = productService.update("missing", buildRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_whenExists_shouldReturnUpdatedProduct() {
        Product existing = buildSavedProduct();
        when(productRepository.findById("prod-123")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenReturn(existing);

        ResponseEntity<ProductResponse> response = productService.update("prod-123", buildRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(productRepository).save(existing);
    }
}
