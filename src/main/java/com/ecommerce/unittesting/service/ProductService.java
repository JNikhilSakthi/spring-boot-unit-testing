package com.ecommerce.unittesting.service;

import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;

import java.math.BigDecimal;
import java.util.List;

// Service Interface — defines the contract (WHAT the service does, not HOW)
//
// WHY use an interface?
//   1. Decouples Controller from implementation
//   2. Easy to swap implementations (ProductServiceImpl → ProductServiceV2Impl)
//   3. Makes mocking easy: @MockBean ProductService (mocks the interface)
//   4. Follows Dependency Inversion Principle (SOLID)
//
// In tests:
//   @ExtendWith  → @Mock repos, @InjectMocks ProductServiceImpl (test the impl directly)
//   @WebMvcTest  → @MockBean ProductService (mock the interface for controller test)
public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long id);

    List<ProductResponse> getAllProducts();

    List<ProductResponse> getProductsByCategory(String category);

    List<ProductResponse> searchProducts(String name);

    List<ProductResponse> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
