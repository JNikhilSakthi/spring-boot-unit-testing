package com.ecommerce.unittesting.controller;

import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;
import com.ecommerce.unittesting.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// @RestController → Combines @Controller + @ResponseBody
//   Every method's return value is automatically converted to JSON
//
// @RequestMapping("/api/products") → Base URL for all endpoints in this controller
//   POST   /api/products           → createProduct
//   GET    /api/products           → getAllProducts
//   GET    /api/products/1         → getProductById
//   PUT    /api/products/1         → updateProduct
//   DELETE /api/products/1         → deleteProduct
//
// @RequiredArgsConstructor → Injects ProductService via constructor
//   Controller depends on Service INTERFACE (not impl) — loose coupling
//
// In @WebMvcTest: ProductService is mocked with @MockBean
// Controller NEVER talks to Repository directly — always goes through Service
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // @PostMapping → handles HTTP POST requests
    // @Valid → triggers validation on ProductRequest (checks @NotBlank, @NotNull, etc.)
    //   If validation fails → MethodArgumentNotValidException → 400 Bad Request
    // @RequestBody → converts JSON request body → ProductRequest Java object
    // ResponseEntity → wraps the response with HTTP status code
    //   ResponseEntity.status(201).body(data) → returns 201 Created with JSON body
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    // @GetMapping("/{id}") → handles GET /api/products/1
    // @PathVariable → extracts "1" from the URL path and maps to 'id' parameter
    // ResponseEntity.ok() → returns 200 OK with JSON body
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // @GetMapping (no path) → handles GET /api/products
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // @PathVariable → extracts "Electronics" from /api/products/category/Electronics
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getProductsByCategory(category));
    }

    // @RequestParam → extracts query parameter from URL
    //   GET /api/products/search?name=iPhone → name = "iPhone"
    // Difference from @PathVariable:
    //   @PathVariable  → /api/products/1          (part of the URL path)
    //   @RequestParam  → /api/products?name=iPhone (after the ? in URL)
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String name) {
        return ResponseEntity.ok(productService.searchProducts(name));
    }

    // Multiple @RequestParam → /api/products/price-range?min=100&max=500
    @GetMapping("/price-range")
    public ResponseEntity<List<ProductResponse>> getProductsByPriceRange(
            @RequestParam BigDecimal min,
            @RequestParam BigDecimal max) {
        return ResponseEntity.ok(productService.getProductsByPriceRange(min, max));
    }

    // @PutMapping → handles HTTP PUT requests (update)
    // Combines @PathVariable (which product) + @RequestBody (new data)
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // @DeleteMapping → handles HTTP DELETE requests
    // ResponseEntity.noContent().build() → returns 204 No Content (no body)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
