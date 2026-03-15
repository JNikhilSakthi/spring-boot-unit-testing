package com.ecommerce.unittesting.springboottest;

import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;
import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;
import com.ecommerce.unittesting.entity.Product;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.ProductRepository;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.ProductService;
import com.ecommerce.unittesting.service.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @SpringBootTest — Full integration test.
 *
 * KEY LEARNING: Product now depends on User. In integration tests,
 * we must create a real User FIRST before creating products.
 * This tests the full flow: User exists → Create Product with userId → Verify UserRef in response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        // Create a real user FIRST — products need a userId
        UserResponse user = userService.createUser(UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("ADMIN")
                .build());
        userId = user.getId();

        productRequest = ProductRequest.builder()
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(userId)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Delete products first (they have FK to users), then users
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== Full Flow: Create Product with User → Read ====================

    @Test
    @DisplayName("Integration: Create product with user, verify UserRef in response")
    void createAndGetProduct_ShouldIncludeUserRef() throws Exception {
        // Create product via POST
        String responseJson = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.createdBy.id").value(userId))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$.updatedBy.id").value(userId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProductResponse created = objectMapper.readValue(responseJson, ProductResponse.class);

        // Fetch and verify UserRef is present
        mockMvc.perform(get("/api/products/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"));
    }

    // ==================== Full Flow: Create → Update with Different User ====================

    @Test
    @DisplayName("Integration: Update product with different user, verify updatedBy changes")
    void updateProduct_WithDifferentUser_ShouldChangeUpdatedBy() throws Exception {
        // Create product as John
        ProductResponse created = productService.createProduct(productRequest);

        // Create another user (Jane — the updater)
        UserResponse admin = userService.createUser(UserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build());

        // Update product as Jane
        ProductRequest updateRequest = ProductRequest.builder()
                .name("iPhone 15 Pro Max")
                .description("Premium Apple smartphone")
                .price(new BigDecimal("1499.99"))
                .quantity(20)
                .category("Electronics")
                .userId(admin.getId())     // Jane is updating
                .build();

        mockMvc.perform(put("/api/products/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15 Pro Max"))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"))    // original
                .andExpect(jsonPath("$.updatedBy.username").value("Jane Smith")); // changed
    }

    // ==================== Create Product with Invalid User ====================

    @Test
    @DisplayName("Integration: Create product with non-existent user should return 404")
    void createProduct_WithInvalidUserId_ShouldReturn404() throws Exception {
        ProductRequest badRequest = ProductRequest.builder()
                .name("iPhone 15")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(9999L)             // user doesn't exist
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 9999"));

        assertEquals(0, productRepository.count());
    }

    // ==================== Full Flow: Create → Delete → Verify ====================

    @Test
    @DisplayName("Integration: Create, delete, and verify removal")
    void createDeleteAndVerify_FullFlow() throws Exception {
        ProductResponse created = productService.createProduct(productRequest);

        mockMvc.perform(delete("/api/products/{id}", created.getId()))
                .andExpect(status().isNoContent());

        assertFalse(productRepository.findById(created.getId()).isPresent());
    }

    // ==================== Multiple Products by Same User ====================

    @Test
    @DisplayName("Integration: Multiple products by same user, search and filter")
    void createMultipleProducts_ByOneUser_FullFlow() throws Exception {
        productService.createProduct(productRequest);

        productService.createProduct(ProductRequest.builder()
                .name("Samsung Galaxy S24")
                .description("Samsung smartphone")
                .price(new BigDecimal("899.99"))
                .quantity(30)
                .category("Electronics")
                .userId(userId)
                .build());

        productService.createProduct(ProductRequest.builder()
                .name("Nike Air Max")
                .description("Running shoes")
                .price(new BigDecimal("149.99"))
                .quantity(100)
                .category("Clothing")
                .userId(userId)
                .build());

        // Get all — 3 products, all created by John
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$[1].createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$[2].createdBy.username").value("John Doe"));

        // Filter by category
        mockMvc.perform(get("/api/products/category/{category}", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ==================== Validation ====================

    @Test
    @DisplayName("Integration: Validation works — missing userId returns 400")
    void createProduct_WithoutUserId_ShouldReturn400() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .name("iPhone 15")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(null)              // missing
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());
    }

    // ==================== Service Layer Directly ====================

    @Test
    @Transactional  // Keeps Hibernate session open for lazy loading when calling service directly
    @DisplayName("Integration: Service layer with real User + Product repos")
    void serviceLayer_ShouldWorkWithBothRepositories() {
        // Create product (service fetches user internally)
        ProductResponse created = productService.createProduct(productRequest);
        assertNotNull(created.getId());
        assertEquals("John Doe", created.getCreatedBy().getUsername());
        assertEquals(userId, created.getCreatedBy().getId());

        // Get and verify
        ProductResponse fetched = productService.getProductById(created.getId());
        assertEquals("iPhone 15", fetched.getName());
        assertEquals("John Doe", fetched.getCreatedBy().getUsername());

        // Delete
        productService.deleteProduct(created.getId());
        assertThrows(ResourceNotFoundException.class,
                () -> productService.getProductById(created.getId()));
    }

    // ==================== NEW INTEGRATION EDGE CASES ====================

    // Edge case: update product with SAME user — createdBy and updatedBy should be same
    @Test
    @DisplayName("Integration: Update product with same user, both refs should match")
    void createAndUpdateProduct_SameUser_ShouldKeepBothRefs() throws Exception {
        // Create product as John
        ProductResponse created = productService.createProduct(productRequest);

        // Update product as John (same user)
        ProductRequest updateRequest = ProductRequest.builder()
                .name("iPhone 15 Updated")
                .description("Updated description")
                .price(new BigDecimal("1099.99"))
                .quantity(40)
                .category("Electronics")
                .userId(userId)  // same user
                .build();

        mockMvc.perform(put("/api/products/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15 Updated"))
                .andExpect(jsonPath("$.createdBy.id").value(userId))
                .andExpect(jsonPath("$.updatedBy.id").value(userId))
                .andExpect(jsonPath("$.createdBy.username").value("John Doe"))
                .andExpect(jsonPath("$.updatedBy.username").value("John Doe"));
    }

    // Edge case: search by price range end-to-end
    @Test
    @DisplayName("Integration: Search products by price range end-to-end")
    void searchProducts_ByPriceRange_EndToEnd() throws Exception {
        // Create products with different prices
        productService.createProduct(productRequest); // 999.99

        productService.createProduct(ProductRequest.builder()
                .name("Budget Phone")
                .price(new BigDecimal("199.99"))
                .quantity(100)
                .category("Electronics")
                .userId(userId)
                .build());

        productService.createProduct(ProductRequest.builder()
                .name("Luxury Watch")
                .price(new BigDecimal("5000.00"))
                .quantity(5)
                .category("Accessories")
                .userId(userId)
                .build());

        // Search in range 100-1000 — should find iPhone (999.99) + Budget Phone (199.99)
        mockMvc.perform(get("/api/products/price-range")
                        .param("min", "100")
                        .param("max", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Search in range 2000-6000 — should find only Luxury Watch (5000.00)
        mockMvc.perform(get("/api/products/price-range")
                        .param("min", "2000")
                        .param("max", "6000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Luxury Watch"));
    }

    // Bug fix test: delete user who has products should return 409
    @Test
    @DisplayName("Integration: Delete user with products should return 409 Conflict")
    void deleteUser_WhenProductsExist_ShouldReturn409() throws Exception {
        // Create a product for the user
        productService.createProduct(productRequest);

        // Try to delete the user — should fail with 409
        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("associated products")));

        // User should still exist
        assertTrue(userRepository.findById(userId).isPresent());
    }
}
