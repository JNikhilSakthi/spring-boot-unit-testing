package com.ecommerce.unittesting.webmvctest;

import com.ecommerce.unittesting.controller.ProductController;
import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;
import com.ecommerce.unittesting.dto.UserRef;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest — Tests ONLY the ProductController web layer.
 * ProductService is mocked. No UserRepository involved here because
 * the controller only talks to ProductService (not repos directly).
 */
@WebMvcTest(controllers = ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRef userRef;
    private ProductRequest productRequest;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        userRef = UserRef.builder()
                .id(1L)
                .username("John Doe")
                .build();

        productRequest = ProductRequest.builder()
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(1L)
                .build();

        productResponse = ProductResponse.builder()
                .id(1L)
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .createdBy(userRef)
                .updatedBy(userRef)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== POST — Create Product ====================

    @Test
    @DisplayName("POST /api/products — Should create product with UserRef and return 201")
    void createProduct_ShouldReturn201WithUserRef() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(productResponse);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.createdBy.id").value(1))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$.updatedBy.id").value(1))
                .andExpect(jsonPath("$.updatedBy.username").value("John Doe"));
    }

    @Test
    @DisplayName("POST /api/products — Should return 400 when userId is null")
    void createProduct_WithoutUserId_ShouldReturn400() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .name("iPhone 15")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(null)                // missing userId
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());
    }

    @Test
    @DisplayName("POST /api/products — Should return 404 when user not found (service throws)")
    void createProduct_WhenUserNotFound_ShouldReturn404() throws Exception {
        when(productService.createProduct(any(ProductRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        ProductRequest request = ProductRequest.builder()
                .name("iPhone 15")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(99L)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 99"));
    }

    @Test
    @DisplayName("POST /api/products — Should return 400 when name is blank")
    void createProduct_WithBlankName_ShouldReturn400() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .name("")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(1L)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ==================== GET — Get By ID ====================

    @Test
    @DisplayName("GET /api/products/1 — Should return product with UserRef")
    void getProductById_ShouldReturn200WithUserRef() throws Exception {
        when(productService.getProductById(1L)).thenReturn(productResponse);

        mockMvc.perform(get("/api/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.createdBy.id").value(1))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"));
    }

    @Test
    @DisplayName("GET /api/products/99 — Should return 404 when not found")
    void getProductById_WhenNotExists_ShouldReturn404() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    // ==================== GET — Get All ====================

    @Test
    @DisplayName("GET /api/products — Should return product list with UserRef")
    void getAllProducts_ShouldReturn200() throws Exception {
        ProductResponse product2 = ProductResponse.builder()
                .id(2L)
                .name("Samsung Galaxy")
                .price(new BigDecimal("899.99"))
                .quantity(30)
                .category("Electronics")
                .createdBy(userRef)
                .updatedBy(userRef)
                .build();

        when(productService.getAllProducts()).thenReturn(Arrays.asList(productResponse, product2));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$[1].createdBy.username").value("John Doe"));
    }

    // ==================== GET — By Category ====================

    @Test
    @DisplayName("GET /api/products/category/Electronics — Should return filtered products")
    void getProductsByCategory_ShouldReturn200() throws Exception {
        when(productService.getProductsByCategory("Electronics")).thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/products/category/{category}", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category").value("Electronics"));
    }

    // ==================== GET — Search ====================

    @Test
    @DisplayName("GET /api/products/search?name=iPhone — Should return matching products")
    void searchProducts_ShouldReturn200() throws Exception {
        when(productService.searchProducts("iPhone")).thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/products/search")
                        .param("name", "iPhone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", containsString("iPhone")));
    }

    // ==================== GET — Price Range ====================

    @Test
    @DisplayName("GET /api/products/price-range — Should return products in range")
    void getProductsByPriceRange_ShouldReturn200() throws Exception {
        when(productService.getProductsByPriceRange(
                new BigDecimal("500"), new BigDecimal("1500")))
                .thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/products/price-range")
                        .param("min", "500")
                        .param("max", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ==================== PUT — Update ====================

    @Test
    @DisplayName("PUT /api/products/1 — Should update with different updatedBy UserRef")
    void updateProduct_ShouldReturn200WithUpdatedUserRef() throws Exception {
        UserRef adminRef = UserRef.builder().id(2L).username("Jane Smith").build();

        ProductResponse updatedResponse = ProductResponse.builder()
                .id(1L)
                .name("iPhone 15 Pro")
                .price(new BigDecimal("1199.99"))
                .quantity(25)
                .category("Electronics")
                .createdBy(userRef)       // original creator
                .updatedBy(adminRef)      // different updater
                .build();

        when(productService.updateProduct(eq(1L), any(ProductRequest.class))).thenReturn(updatedResponse);

        ProductRequest updateRequest = ProductRequest.builder()
                .name("iPhone 15 Pro")
                .description("Updated")
                .price(new BigDecimal("1199.99"))
                .quantity(25)
                .category("Electronics")
                .userId(2L)
                .build();

        mockMvc.perform(put("/api/products/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$.updatedBy.username").value("Jane Smith"));
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("DELETE /api/products/1 — Should delete and return 204")
    void deleteProduct_ShouldReturn204() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(productService, times(1)).deleteProduct(1L);
    }

    @Test
    @DisplayName("DELETE /api/products/99 — Should return 404 when not found")
    void deleteProduct_WhenNotExists_ShouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99"))
                .when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/products/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
