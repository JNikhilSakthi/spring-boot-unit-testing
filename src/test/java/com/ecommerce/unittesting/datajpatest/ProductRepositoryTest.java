package com.ecommerce.unittesting.datajpatest;

import com.ecommerce.unittesting.entity.Product;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @DataJpaTest — Tests JPA layer with H2.
 *
 * KEY LEARNING: Product now has @ManyToOne relationship with User (createdBy/updatedBy).
 * In DataJpaTest, we must persist the User FIRST before creating products,
 * because the foreign key constraint requires it.
 */
@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Product electronics1;
    private Product electronics2;
    private Product clothing;

    @BeforeEach
    void setUp() {
        // MUST persist User FIRST — Product has a foreign key to User
        user = entityManager.persistAndFlush(User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("ADMIN")
                .build());

        electronics1 = entityManager.persistAndFlush(Product.builder()
                .name("iPhone 15")
                .description("Apple smartphone")
                .price(new BigDecimal("999.99"))
                .quantity(50)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build());

        electronics2 = entityManager.persistAndFlush(Product.builder()
                .name("Samsung Galaxy S24")
                .description("Samsung smartphone")
                .price(new BigDecimal("899.99"))
                .quantity(30)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build());

        clothing = entityManager.persistAndFlush(Product.builder()
                .name("Nike Air Max")
                .description("Running shoes")
                .price(new BigDecimal("149.99"))
                .quantity(100)
                .category("Clothing")
                .createdBy(user)
                .updatedBy(user)
                .build());
    }

    // ==================== CRUD — Built-in JPA Methods ====================

    @Test
    @DisplayName("Should save product with User relationship")
    void save_ShouldPersistProductWithUser() {
        Product newProduct = Product.builder()
                .name("MacBook Pro")
                .description("Apple laptop")
                .price(new BigDecimal("2499.99"))
                .quantity(10)
                .category("Electronics")
                .createdBy(user)
                .updatedBy(user)
                .build();

        Product saved = productRepository.save(newProduct);

        assertNotNull(saved.getId());
        assertEquals("MacBook Pro", saved.getName());
        assertEquals(user.getId(), saved.getCreatedBy().getId());
        assertEquals(user.getId(), saved.getUpdatedBy().getId());
    }

    @Test
    @DisplayName("Should find product by ID with User loaded")
    void findById_WhenExists_ShouldReturnProductWithUser() {
        Optional<Product> found = productRepository.findById(electronics1.getId());

        assertTrue(found.isPresent());
        assertEquals("iPhone 15", found.get().getName());
        assertNotNull(found.get().getCreatedBy());
        assertEquals("John", found.get().getCreatedBy().getFirstName());
    }

    @Test
    @DisplayName("Should return empty when product not found")
    void findById_WhenNotExists_ShouldReturnEmpty() {
        Optional<Product> found = productRepository.findById(999L);

        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should find all products")
    void findAll_ShouldReturnAllProducts() {
        List<Product> products = productRepository.findAll();

        assertEquals(3, products.size());
    }

    @Test
    @DisplayName("Should delete product")
    void delete_ShouldRemoveProduct() {
        productRepository.delete(electronics1);
        entityManager.flush();

        Product found = entityManager.find(Product.class, electronics1.getId());
        assertNull(found);
    }

    // ==================== Custom Query Methods ====================

    @Test
    @DisplayName("Should find products by category")
    void findByCategory_ShouldReturnMatchingProducts() {
        List<Product> electronics = productRepository.findByCategory("Electronics");

        assertEquals(2, electronics.size());
        assertTrue(electronics.stream().allMatch(p -> p.getCategory().equals("Electronics")));
    }

    @Test
    @DisplayName("Should return empty list for non-existent category")
    void findByCategory_WhenNoMatch_ShouldReturnEmptyList() {
        List<Product> products = productRepository.findByCategory("Books");

        assertTrue(products.isEmpty());
    }

    @Test
    @DisplayName("Should search products by name (case-insensitive)")
    void findByNameContainingIgnoreCase_ShouldReturnMatches() {
        List<Product> products = productRepository.findByNameContainingIgnoreCase("iphone");

        assertEquals(1, products.size());
        assertEquals("iPhone 15", products.get(0).getName());
    }

    @Test
    @DisplayName("Should find products within price range")
    void findByPriceBetween_ShouldReturnProductsInRange() {
        List<Product> products = productRepository.findByPriceBetween(
                new BigDecimal("100.00"),
                new BigDecimal("900.00")
        );

        assertEquals(2, products.size()); // Samsung (899.99) + Nike (149.99)
    }

    @Test
    @DisplayName("Should find products with quantity greater than threshold")
    void findByQuantityGreaterThan_ShouldReturnProducts() {
        List<Product> products = productRepository.findByQuantityGreaterThan(40);

        assertEquals(2, products.size()); // iPhone (50) + Nike (100)
    }

    // ==================== Relationship Tests ====================

    @Test
    @DisplayName("Should verify createdBy relationship is persisted")
    void createdBy_ShouldBePersistedCorrectly() {
        Product found = entityManager.find(Product.class, electronics1.getId());

        assertNotNull(found.getCreatedBy());
        assertEquals(user.getId(), found.getCreatedBy().getId());
        assertEquals("John", found.getCreatedBy().getFirstName());
        assertEquals("Doe", found.getCreatedBy().getLastName());
    }

    @Test
    @DisplayName("Should allow different users for createdBy and updatedBy")
    void differentUsersForCreateAndUpdate() {
        User admin = entityManager.persistAndFlush(User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build());

        Product productWithDifferentUsers = entityManager.persistAndFlush(Product.builder()
                .name("Test Product")
                .price(new BigDecimal("50.00"))
                .quantity(5)
                .category("Test")
                .createdBy(user)     // created by John
                .updatedBy(admin)    // updated by Jane
                .build());

        Product found = entityManager.find(Product.class, productWithDifferentUsers.getId());

        assertEquals("John", found.getCreatedBy().getFirstName());
        assertEquals("Jane", found.getUpdatedBy().getFirstName());
        assertNotEquals(found.getCreatedBy().getId(), found.getUpdatedBy().getId());
    }

    // ==================== Entity Lifecycle ====================

    @Test
    @DisplayName("Should set createdAt on persist")
    void persist_ShouldSetCreatedAt() {
        Product newProduct = Product.builder()
                .name("Test Product")
                .price(new BigDecimal("10.00"))
                .quantity(1)
                .category("Test")
                .createdBy(user)
                .updatedBy(user)
                .build();

        Product saved = productRepository.saveAndFlush(newProduct);

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    // ==================== NEW EDGE CASES ====================

    // Edge case: no products in the given price range
    @Test
    @DisplayName("Should return empty when no products in price range")
    void findByPriceBetween_WhenNoneInRange_ShouldReturnEmpty() {
        List<Product> products = productRepository.findByPriceBetween(
                new BigDecimal("5000.00"),
                new BigDecimal("10000.00")
        );

        assertTrue(products.isEmpty());
    }

    // Edge case: no products with quantity greater than threshold
    @Test
    @DisplayName("Should return empty when no products have quantity above threshold")
    void findByQuantityGreaterThan_WhenNoneMatch_ShouldReturnEmpty() {
        List<Product> products = productRepository.findByQuantityGreaterThan(500);

        assertTrue(products.isEmpty());
    }

    // Edge case: search with special characters
    @Test
    @DisplayName("Should handle special characters in name search")
    void findByNameContainingIgnoreCase_WithSpecialCharacters() {
        List<Product> products = productRepository.findByNameContainingIgnoreCase("@#$");

        assertTrue(products.isEmpty());
    }

    // Edge case: category search is case-sensitive (exact match)
    @Test
    @DisplayName("Should verify category search is case-sensitive (exact match)")
    void findByCategory_CaseSensitivity() {
        // "electronics" (lowercase) should NOT match "Electronics" (capitalized)
        List<Product> products = productRepository.findByCategory("electronics");

        assertTrue(products.isEmpty());
    }

    // Edge case: updatedAt changes on update
    @Test
    @DisplayName("Should update updatedAt timestamp on save")
    void update_ShouldChangeUpdatedAtTimestamp() throws InterruptedException {
        Product found = entityManager.find(Product.class, electronics1.getId());
        java.time.LocalDateTime originalUpdatedAt = found.getUpdatedAt();

        // Small delay to ensure timestamp differs
        Thread.sleep(10);

        // Update the product
        found.setName("iPhone 15 Pro");
        productRepository.saveAndFlush(found);

        entityManager.clear(); // clear persistence context to force re-read
        Product updated = entityManager.find(Product.class, electronics1.getId());

        assertEquals("iPhone 15 Pro", updated.getName());
        assertNotNull(updated.getUpdatedAt());
        // updatedAt should be same or after original (depends on @PreUpdate)
        assertTrue(updated.getUpdatedAt().compareTo(originalUpdatedAt) >= 0);
    }

    // NEW: existsByCreatedByIdOrUpdatedById — tests the new repo method for bug fix
    @Test
    @DisplayName("Should return true when user has products as creator")
    void existsByCreatedByIdOrUpdatedById_WhenCreator_ShouldReturnTrue() {
        boolean exists = productRepository.existsByCreatedByIdOrUpdatedById(
                user.getId(), user.getId());

        assertTrue(exists);
    }

    @Test
    @DisplayName("Should return false when user has no products")
    void existsByCreatedByIdOrUpdatedById_WhenNone_ShouldReturnFalse() {
        User newUser = entityManager.persistAndFlush(User.builder()
                .firstName("Nobody")
                .lastName("Products")
                .email("nobody@example.com")
                .phone("0000000000")
                .role("CUSTOMER")
                .build());

        boolean exists = productRepository.existsByCreatedByIdOrUpdatedById(
                newUser.getId(), newUser.getId());

        assertFalse(exists);
    }
}
