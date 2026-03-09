package com.ecommerce.unittesting.extendwith;

import com.ecommerce.unittesting.dto.ProductRequest;
import com.ecommerce.unittesting.dto.ProductResponse;
import com.ecommerce.unittesting.entity.Product;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.ProductRepository;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ========================= @ExtendWith — TWO MOCK DEPENDENCIES =========================
 *
 * KEY LEARNING: ProductServiceImpl now depends on BOTH ProductRepository AND UserRepository.
 * We mock BOTH dependencies with @Mock. Mockito injects both into @InjectMocks automatically.
 *
 * This is a common real-world scenario:
 *   - Creating a product requires looking up the user (createdBy/updatedBy)
 *   - So the service needs TWO repositories
 *   - In tests, we mock BOTH and control their behavior independently
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;   // MOCK #1 — fake product repo

    @Mock
    private UserRepository userRepository;          // MOCK #2 — fake user repo

    @InjectMocks
    private ProductServiceImpl productService;      // REAL service — both mocks injected

    private User user;
    private Product product;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("ADMIN")
                .build();

        product = Product.builder()
                .id(1L)
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build();

        productRequest = ProductRequest.builder()
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(1L)
                .build();
    }

    // ==================== CREATE — Now requires User lookup ====================

    @Test
    @DisplayName("Should create product with valid user — both repos called")
    void createProduct_WithValidUser_ShouldReturnSavedProduct() {
        // ARRANGE — mock BOTH repositories
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));         // user exists
        when(productRepository.save(any(Product.class))).thenReturn(product);    // product saved

        // ACT
        ProductResponse response = productService.createProduct(productRequest);

        // ASSERT
        assertNotNull(response);
        assertEquals("iPhone 15", response.getName());
        assertEquals(1L, response.getCreatedBy().getId());
        assertEquals("John Doe", response.getCreatedBy().getUsername());
        assertEquals(1L, response.getUpdatedBy().getId());
        assertEquals("John Doe", response.getUpdatedBy().getUsername());

        // VERIFY — both repos were called
        verify(userRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found during product creation")
    void createProduct_WhenUserNotFound_ShouldThrowException() {
        // ARRANGE — user does NOT exist
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ProductRequest badRequest = ProductRequest.builder()
                .name("iPhone 15")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .userId(99L)
                .build();

        // ACT & ASSERT
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.createProduct(badRequest)
        );

        assertEquals("User not found with id: 99", exception.getMessage());

        // VERIFY — product was NEVER saved because user doesn't exist
        verify(userRepository, times(1)).findById(99L);
        verify(productRepository, never()).save(any(Product.class));
    }

    // ==================== GET BY ID ====================

    @Test
    @DisplayName("Should return product with UserRef when found by ID")
    void getProductById_WhenExists_ShouldReturnProductWithUserRef() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("iPhone 15", response.getName());
        assertNotNull(response.getCreatedBy());
        assertEquals(1L, response.getCreatedBy().getId());
        assertEquals("John Doe", response.getCreatedBy().getUsername());
    }

    @Test
    @DisplayName("Should throw exception when product not found")
    void getProductById_WhenNotExists_ShouldThrowException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.getProductById(99L)
        );

        assertEquals("Product not found with id: 99", exception.getMessage());
    }

    // ==================== GET ALL ====================

    @Test
    @DisplayName("Should return all products with UserRef")
    void getAllProducts_ShouldReturnProductList() {
        Product product2 = Product.builder()
                .id(2L)
                .name("Samsung Galaxy")
                .price(new BigDecimal("899.99"))
                .quantity(30)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build();

        when(productRepository.findAll()).thenReturn(Arrays.asList(product, product2));

        List<ProductResponse> products = productService.getAllProducts();

        assertEquals(2, products.size());
        assertEquals("iPhone 15", products.get(0).getName());
        assertEquals("Samsung Galaxy", products.get(1).getName());
        // Both products created by same user
        assertEquals("John Doe", products.get(0).getCreatedBy().getUsername());
        assertEquals("John Doe", products.get(1).getCreatedBy().getUsername());
    }

    @Test
    @DisplayName("Should return empty list when no products")
    void getAllProducts_WhenEmpty_ShouldReturnEmptyList() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<ProductResponse> products = productService.getAllProducts();

        assertTrue(products.isEmpty());
    }

    // ==================== GET BY CATEGORY ====================

    @Test
    @DisplayName("Should return products by category")
    void getProductsByCategory_ShouldReturnFilteredProducts() {
        when(productRepository.findByCategory("Electronics")).thenReturn(List.of(product));

        List<ProductResponse> products = productService.getProductsByCategory("Electronics");

        assertEquals(1, products.size());
        assertEquals("Electronics", products.get(0).getCategory());
    }

    // ==================== SEARCH ====================

    @Test
    @DisplayName("Should search products by name")
    void searchProducts_ShouldReturnMatchingProducts() {
        when(productRepository.findByNameContainingIgnoreCase("iPhone")).thenReturn(List.of(product));

        List<ProductResponse> products = productService.searchProducts("iPhone");

        assertEquals(1, products.size());
        assertEquals("iPhone 15", products.get(0).getName());
    }

    // ==================== UPDATE — Also requires User lookup ====================

    @Test
    @DisplayName("Should update product with different user — updatedBy changes, createdBy stays")
    void updateProduct_WithDifferentUser_ShouldUpdateUserRef() {
        User adminUser = User.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .build();

        ProductRequest updateRequest = ProductRequest.builder()
                .name("iPhone 15 Pro")
                .description("Updated Apple smartphone")
                .price(new BigDecimal("1199.99"))
                .quantity(25)
                .category("Electronics")
                .userId(2L)           // different user updating
                .build();

        Product updatedProduct = Product.builder()
                .id(1L)
                .name("iPhone 15 Pro")
                .description("Updated Apple smartphone")
                .price(new BigDecimal("1199.99"))
                .quantity(25)
                .category("Electronics")
                .createdBy(user)       // original creator stays
                .updatedBy(adminUser)  // new updater
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

        // ACT
        ProductResponse response = productService.updateProduct(1L, updateRequest);

        // ASSERT — createdBy unchanged, updatedBy changed
        assertEquals("iPhone 15 Pro", response.getName());
        assertEquals("John Doe", response.getCreatedBy().getUsername());   // original
        assertEquals(1L, response.getCreatedBy().getId());
        assertEquals("Jane Smith", response.getUpdatedBy().getUsername()); // new updater
        assertEquals(2L, response.getUpdatedBy().getId());

        // VERIFY
        verify(productRepository).findById(1L);
        verify(userRepository).findById(2L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw exception when updating user not found")
    void updateProduct_WhenUserNotFound_ShouldThrowException() {
        ProductRequest updateRequest = ProductRequest.builder()
                .name("iPhone 15 Pro")
                .price(new BigDecimal("1199.99"))
                .quantity(25)
                .category("Electronics")
                .userId(99L)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct(1L, updateRequest));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent product")
    void updateProduct_WhenProductNotFound_ShouldThrowException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct(99L, productRequest));

        verify(userRepository, never()).findById(any());
        verify(productRepository, never()).save(any(Product.class));
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("Should delete product successfully")
    void deleteProduct_WhenExists_ShouldDeleteSuccessfully() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doNothing().when(productRepository).delete(product);

        assertDoesNotThrow(() -> productService.deleteProduct(1L));

        verify(productRepository, times(1)).delete(product);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent product")
    void deleteProduct_WhenNotExists_ShouldThrowException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.deleteProduct(99L));

        verify(productRepository, never()).delete(any(Product.class));
    }

    // ==================== NEW EDGE CASES ====================

    // Edge case: product with zero quantity (boundary value)
    @Test
    @DisplayName("Should create product with zero quantity — boundary value")
    void createProduct_WithZeroQuantity_ShouldSucceed() {
        ProductRequest zeroQtyRequest = ProductRequest.builder()
                .name("Limited Edition")
                .description("Out of stock item")
                .price(new BigDecimal("499.99"))
                .quantity(0)
                .category("Electronics")
                .userId(1L)
                .build();

        Product zeroQtyProduct = Product.builder()
                .id(2L)
                .name("Limited Edition")
                .description("Out of stock item")
                .price(new BigDecimal("499.99"))
                .quantity(0)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.save(any(Product.class))).thenReturn(zeroQtyProduct);

        ProductResponse response = productService.createProduct(zeroQtyRequest);

        assertEquals(0, response.getQuantity());
        assertEquals("Limited Edition", response.getName());
    }

    // Edge case: very large price (BigDecimal boundary)
    @Test
    @DisplayName("Should create product with very large price — BigDecimal boundary")
    void createProduct_WithMaxPrice_ShouldSucceed() {
        BigDecimal largePrice = new BigDecimal("99999999.99");

        ProductRequest maxPriceRequest = ProductRequest.builder()
                .name("Luxury Item")
                .price(largePrice)
                .quantity(1)
                .category("Luxury")
                .userId(1L)
                .build();

        Product maxPriceProduct = Product.builder()
                .id(3L)
                .name("Luxury Item")
                .price(largePrice)
                .quantity(1)
                .category("Luxury")
                .createdBy(user)
                .updatedBy(user)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.save(any(Product.class))).thenReturn(maxPriceProduct);

        ProductResponse response = productService.createProduct(maxPriceRequest);

        assertEquals(largePrice, response.getPrice());
    }

    // Edge case: empty search string
    @Test
    @DisplayName("Should handle empty search string")
    void searchProducts_WithEmptyString_ShouldReturnResults() {
        when(productRepository.findByNameContainingIgnoreCase("")).thenReturn(List.of(product));

        List<ProductResponse> products = productService.searchProducts("");

        assertEquals(1, products.size());
        verify(productRepository).findByNameContainingIgnoreCase("");
    }

    // Edge case: multiple categories — verify correct filtering
    @Test
    @DisplayName("Should return only matching category when multiple exist")
    void getAllProducts_WhenMultipleCategories_ShouldFilterCorrectly() {
        Product clothingProduct = Product.builder()
                .id(2L)
                .name("Nike Shoes")
                .price(new BigDecimal("149.99"))
                .quantity(100)
                .category("Clothing")
                .createdBy(user)
                .updatedBy(user)
                .build();

        // Only return electronics when filtering
        when(productRepository.findByCategory("Electronics")).thenReturn(List.of(product));

        List<ProductResponse> electronics = productService.getProductsByCategory("Electronics");

        assertEquals(1, electronics.size());
        assertEquals("Electronics", electronics.get(0).getCategory());
        assertEquals("iPhone 15", electronics.get(0).getName());
    }

    // Edge case: price range where min > max
    @Test
    @DisplayName("Should return empty list when min price > max price")
    void getProductsByPriceRange_WhenMinGreaterThanMax_ShouldReturnEmpty() {
        when(productRepository.findByPriceBetween(
                new BigDecimal("1000"), new BigDecimal("100")))
                .thenReturn(List.of());

        List<ProductResponse> products = productService.getProductsByPriceRange(
                new BigDecimal("1000"), new BigDecimal("100"));

        assertTrue(products.isEmpty());
    }
}
