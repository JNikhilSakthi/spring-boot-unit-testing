package com.ecommerce.unittesting.datajpatest;

import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @DataJpaTest — Tests ONLY the JPA/Repository layer with H2 in-memory DB.
 * Each test auto-rolls back. No controllers or services loaded.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User customer1;
    private User customer2;
    private User admin;

    @BeforeEach
    void setUp() {
        customer1 = entityManager.persistAndFlush(User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build());

        customer2 = entityManager.persistAndFlush(User.builder()
                .firstName("Johnny")
                .lastName("Bravo")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("CUSTOMER")
                .build());

        admin = entityManager.persistAndFlush(User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build());
    }

    // ==================== CRUD — Built-in JPA Methods ====================

    @Test
    @DisplayName("Should save and retrieve user by ID")
    void save_ShouldPersistUser() {
        User newUser = User.builder()
                .firstName("Bob")
                .lastName("Builder")
                .email("bob@example.com")
                .phone("5555555555")
                .role("CUSTOMER")
                .build();

        User saved = userRepository.save(newUser);

        assertNotNull(saved.getId());
        assertEquals("Bob", saved.getFirstName());

        User found = entityManager.find(User.class, saved.getId());
        assertEquals("Bob", found.getFirstName());
    }

    @Test
    @DisplayName("Should find user by ID")
    void findById_WhenExists_ShouldReturnUser() {
        Optional<User> found = userRepository.findById(customer1.getId());

        assertTrue(found.isPresent());
        assertEquals("John", found.get().getFirstName());
    }

    @Test
    @DisplayName("Should return empty when user not found")
    void findById_WhenNotExists_ShouldReturnEmpty() {
        Optional<User> found = userRepository.findById(999L);

        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should find all users")
    void findAll_ShouldReturnAllUsers() {
        List<User> users = userRepository.findAll();

        assertEquals(3, users.size());
    }

    @Test
    @DisplayName("Should delete user")
    void delete_ShouldRemoveUser() {
        userRepository.delete(customer1);
        entityManager.flush();

        User found = entityManager.find(User.class, customer1.getId());
        assertNull(found);
    }

    // ==================== Custom Query Methods ====================

    @Test
    @DisplayName("Should find user by email")
    void findByEmail_WhenExists_ShouldReturnUser() {
        Optional<User> found = userRepository.findByEmail("john@example.com");

        assertTrue(found.isPresent());
        assertEquals("John", found.get().getFirstName());
        assertEquals("john@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("Should return empty for non-existent email")
    void findByEmail_WhenNotExists_ShouldReturnEmpty() {
        Optional<User> found = userRepository.findByEmail("nobody@example.com");

        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should find users by role")
    void findByRole_ShouldReturnMatchingUsers() {
        List<User> customers = userRepository.findByRole("CUSTOMER");

        assertEquals(2, customers.size());
        assertTrue(customers.stream().allMatch(u -> u.getRole().equals("CUSTOMER")));
    }

    @Test
    @DisplayName("Should return empty list for non-existent role")
    void findByRole_WhenNoMatch_ShouldReturnEmptyList() {
        List<User> users = userRepository.findByRole("SUPERADMIN");

        assertTrue(users.isEmpty());
    }

    @Test
    @DisplayName("Should search users by first name (case-insensitive)")
    void findByFirstNameContainingIgnoreCase_ShouldReturnMatches() {
        // Search "john" (lowercase) — should find "John" and "Johnny"
        List<User> users = userRepository.findByFirstNameContainingIgnoreCase("john");

        assertEquals(2, users.size());
    }

    @Test
    @DisplayName("Should search users by partial first name")
    void findByFirstNameContainingIgnoreCase_PartialMatch() {
        // Search "Jan" — should find "Jane"
        List<User> users = userRepository.findByFirstNameContainingIgnoreCase("Jan");

        assertEquals(1, users.size());
        assertEquals("Jane", users.get(0).getFirstName());
    }

    @Test
    @DisplayName("Should return true when email exists")
    void existsByEmail_WhenExists_ShouldReturnTrue() {
        boolean exists = userRepository.existsByEmail("john@example.com");

        assertTrue(exists);
    }

    @Test
    @DisplayName("Should return false when email does not exist")
    void existsByEmail_WhenNotExists_ShouldReturnFalse() {
        boolean exists = userRepository.existsByEmail("nobody@example.com");

        assertFalse(exists);
    }

    // ==================== Entity Lifecycle ====================

    @Test
    @DisplayName("Should set createdAt and updatedAt on persist")
    void persist_ShouldSetTimestamps() {
        User newUser = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phone("0000000000")
                .role("CUSTOMER")
                .build();

        User saved = userRepository.saveAndFlush(newUser);

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    // ==================== Unique Constraint ====================

    @Test
    @DisplayName("Should fail when saving duplicate email")
    void save_WithDuplicateEmail_ShouldThrowException() {
        User duplicateUser = User.builder()
                .firstName("Duplicate")
                .lastName("User")
                .email("john@example.com")   // same email as customer1
                .phone("9999999999")
                .role("CUSTOMER")
                .build();

        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(duplicateUser);
        });
    }

    // ==================== NEW: existsByEmailAndIdNot tests (for update bug fix) ====================

    // When ANOTHER user has this email → should return true
    @Test
    @DisplayName("Should return true when another user has the email")
    void existsByEmailAndIdNot_WhenAnotherUserHasEmail_ShouldReturnTrue() {
        // customer1 has "john@example.com", check if someone OTHER than customer2 has it
        boolean exists = userRepository.existsByEmailAndIdNot("john@example.com", customer2.getId());

        assertTrue(exists); // customer1 has it, and customer1 != customer2
    }

    // When the SAME user has this email → should return false (no conflict with self)
    @Test
    @DisplayName("Should return false when same user has the email (no self-conflict)")
    void existsByEmailAndIdNot_WhenSameUserHasEmail_ShouldReturnFalse() {
        // customer1 has "john@example.com", check excluding customer1's own ID
        boolean exists = userRepository.existsByEmailAndIdNot("john@example.com", customer1.getId());

        assertFalse(exists); // only customer1 has it, and we excluded customer1
    }

    // When no one has this email → should return false
    @Test
    @DisplayName("Should return false when no one has the email")
    void existsByEmailAndIdNot_WhenNoOneHasEmail_ShouldReturnFalse() {
        boolean exists = userRepository.existsByEmailAndIdNot("new@example.com", customer1.getId());

        assertFalse(exists);
    }
}
