package org.cedro.inventoryservice.service.impl;

import com.cedro.orderrestservice.rest.service.impl.AbstractService;
import org.cedro.inventoryservice.repository.ProductRepository;
import org.cedro.inventoryservice.service.ProductService;
import org.cedro.ordermodel.dto.ProductRequest;
import org.cedro.ordermodel.dto.ProductResponse;
import org.cedro.ordermodel.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends AbstractService<ProductRequest, ProductResponse, Product> implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    protected Product toEntity(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setBrand(request.brand());
        product.setImageUrl(request.imageUrl());
        product.setSize(request.size());
        product.setColor(request.color());
        product.setMaterial(request.material());
        product.setWeight(request.weight());
        product.setIsActive(true);
        return product;
    }

    @Override
    protected Product updateEntity(Product existing, ProductRequest request) {
        existing.setName(request.name());
        existing.setDescription(request.description());
        existing.setCategory(request.category());
        existing.setPrice(request.price());
        existing.setStockQuantity(request.stockQuantity());
        existing.setBrand(request.brand());
        existing.setImageUrl(request.imageUrl());
        existing.setSize(request.size());
        existing.setColor(request.color());
        existing.setMaterial(request.material());
        existing.setWeight(request.weight());
        return existing;
    }

    @Override
    protected ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getIsActive(),
                product.getBrand(),
                product.getImageUrl(),
                product.getSize(),
                product.getColor(),
                product.getMaterial(),
                product.getWeight(),
                product.getRating(),
                product.getReviewCount()
        );
    }

    @Override
    protected MongoRepository<Product, String> getRepository() {
        return productRepository;
    }

    @Override
    protected Class<Product> getEntityClass() {
        return Product.class;
    }
}
