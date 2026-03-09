package com.ecommerce.unittesting.service.impl;

import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;
import com.ecommerce.unittesting.dto.UserRef;
import com.ecommerce.unittesting.entity.Product;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.ProductRepository;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// @Service → Marks this as a Spring service bean (business logic layer)
// @RequiredArgsConstructor → Lombok generates constructor for all 'final' fields
//   This is how Spring injects dependencies (constructor injection):
//   Spring sees: "ProductServiceImpl needs ProductRepository and UserRepository"
//   Spring creates both and passes them via the constructor
//
// TWO DEPENDENCIES:
//   ProductRepository → for product CRUD operations
//   UserRepository    → for looking up the user who created/updated the product
//
// In @ExtendWith tests: both are mocked with @Mock, injected via @InjectMocks
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // CREATE — converts DTO → Entity, saves to DB, returns Response DTO
    // Flow: ProductRequest → look up User → build Product → save → map to ProductResponse
    @Override
    public ProductResponse createProduct(ProductRequest request) {
        // Step 1: Look up the user who is creating this product
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        // Step 2: Build entity from request + set the user as creator
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .category(request.getCategory())
                .createdBy(user)
                .updatedBy(user)
                .build();

        // Step 3: Save to DB and convert entity → response DTO
        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    // READ — find by ID, throw 404 if not found
    @Override
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    // READ ALL — stream converts List<Product> → List<ProductResponse>
    // .stream()              → creates a stream from the list
    // .map(this::mapToResponse) → transforms each Product → ProductResponse
    // .collect(Collectors.toList()) → collects back into a List
    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> getProductsByCategory(String category) {
        return productRepository.findByCategory(category)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // UPDATE — find product, find user, update fields, save
    // Note: createdBy stays the same, only updatedBy changes
    @Override
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Look up the user who is updating (may be different from creator)
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity());
        product.setCategory(request.getCategory());
        product.setUpdatedBy(user);

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    // MAPPER — converts User entity → UserRef DTO (lightweight reference)
    // Only exposes id + username, not full user details
    private UserRef mapToUserRef(User user) {
        return UserRef.builder()
                .id(user.getId())
                .username(user.getFirstName() + " " + user.getLastName())
                .build();
    }

    // MAPPER — converts Product entity → ProductResponse DTO
    // This is where entity fields are collected and set to the response
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .quantity(product.getQuantity())
                .category(product.getCategory())
                .createdBy(mapToUserRef(product.getCreatedBy()))
                .updatedBy(mapToUserRef(product.getUpdatedBy()))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
