package org.cedro.inventoryservice.service;

import com.cedro.orderrestservice.rest.Rest;
import org.cedro.ordermodel.dto.ProductRequest;
import org.cedro.ordermodel.dto.ProductResponse;

public interface ProductService extends Rest<ProductRequest, ProductResponse> {
}
