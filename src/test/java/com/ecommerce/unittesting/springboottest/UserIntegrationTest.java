package com.ecommerce.unittesting.springboottest;

import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @SpringBootTest — Full integration test for User domain.
 * Loads the entire Spring context. Tests all layers working together.
 * Uses H2 in-memory DB (test profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRequest userRequest;

    @BeforeEach
    void setUp() {
        userRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // ==================== Full Flow: Create → Read ====================

    @Test
    @DisplayName("Integration: Create user via API, then fetch it — full flow")
    void createAndGetUser_FullFlow() throws Exception {
        // STEP 1: Create user via POST
        String responseJson = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UserResponse created = objectMapper.readValue(responseJson, UserResponse.class);
        Long userId = created.getId();

        // STEP 2: Fetch user via GET
        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.firstName").value("John"));

        // STEP 3: Fetch by email
        mockMvc.perform(get("/api/users/email/{email}", "john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"));

        // STEP 4: Verify in DB
        assertTrue(userRepository.findById(userId).isPresent());
    }

    // ==================== Full Flow: Create → Update → Verify ====================

    @Test
    @DisplayName("Integration: Create, update, and verify user")
    void createUpdateAndVerify_FullFlow() throws Exception {
        // Create
        UserResponse created = userService.createUser(userRequest);
        Long userId = created.getId();

        // Update via PUT
        UserRequest updateRequest = UserRequest.builder()
                .firstName("Johnny")
                .lastName("Doe Updated")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("ADMIN")
                .build();

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Verify in DB
        User fromDb = userRepository.findById(userId).orElseThrow();
        assertEquals("Johnny", fromDb.getFirstName());
        assertEquals("ADMIN", fromDb.getRole());
    }

    // ==================== Full Flow: Create → Delete → Verify Gone ====================

    @Test
    @DisplayName("Integration: Create, delete, and verify removal")
    void createDeleteAndVerify_FullFlow() throws Exception {
        // Create
        UserResponse created = userService.createUser(userRequest);
        Long userId = created.getId();

        // Delete via DELETE
        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isNoContent());

        // Verify gone from DB
        assertFalse(userRepository.findById(userId).isPresent());

        // Verify GET returns 404
        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isNotFound());
    }

    // ==================== Duplicate Email Prevention ====================

    @Test
    @DisplayName("Integration: Should prevent duplicate email creation")
    void createDuplicateEmail_ShouldReturn409() throws Exception {
        // Create first user
        userService.createUser(userRequest);

        // Try to create another user with same email
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists with email: john@example.com"));

        // Verify only 1 user in DB
        assertEquals(1, userRepository.count());
    }

    // ==================== Multiple Users — Search and Filter ====================

    @Test
    @DisplayName("Integration: Create multiple users, search and filter")
    void createMultipleAndSearch_FullFlow() throws Exception {
        // Create 3 users
        userService.createUser(userRequest);

        userService.createUser(UserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build());

        userService.createUser(UserRequest.builder()
                .firstName("Johnny")
                .lastName("Bravo")
                .email("johnny@example.com")
                .phone("5555555555")
                .role("CUSTOMER")
                .build());

        // Get all — should return 3
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Filter by role — should return 2 customers
        mockMvc.perform(get("/api/users/role/{role}", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Search by name — "John" should match "John" and "Johnny"
        mockMvc.perform(get("/api/users/search")
                        .param("firstName", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ==================== Validation End-to-End ====================

    @Test
    @DisplayName("Integration: Validation works through all layers")
    void createUser_WithInvalidData_ShouldReturn400() throws Exception {
        UserRequest invalidRequest = UserRequest.builder()
                .firstName("")
                .lastName("")
                .email("not-an-email")
                .phone("")
                .role("")
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        // Verify nothing saved
        assertEquals(0, userRepository.count());
    }

    // ==================== Service Layer Directly ====================

    @Test
    @DisplayName("Integration: Service layer works with real repository")
    void serviceLayer_ShouldWorkWithRealRepository() {
        // Create
        UserResponse created = userService.createUser(userRequest);
        assertNotNull(created.getId());

        // Get by ID
        UserResponse fetched = userService.getUserById(created.getId());
        assertEquals("John", fetched.getFirstName());

        // Get by email
        UserResponse byEmail = userService.getUserByEmail("john@example.com");
        assertEquals("john@example.com", byEmail.getEmail());

        // Get all
        List<UserResponse> all = userService.getAllUsers();
        assertEquals(1, all.size());

        // Delete
        userService.deleteUser(created.getId());

        // Verify deleted
        assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserById(created.getId()));
    }

    // ==================== NEW INTEGRATION EDGE CASES ====================

    // Bug fix test: update user's email to another user's email should return 409
    @Test
    @DisplayName("Integration: Update user with another user's email should return 409")
    void updateUser_WithAnotherUsersEmail_ShouldReturn409() throws Exception {
        // Create two users
        UserResponse john = userService.createUser(userRequest);

        userService.createUser(UserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build());

        // Try to update John's email to Jane's email
        UserRequest conflictRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("jane@example.com")  // Jane's email!
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        mockMvc.perform(put("/api/users/{id}", john.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use: jane@example.com"));

        // Verify John's email didn't change in DB
        User fromDb = userRepository.findById(john.getId()).orElseThrow();
        assertEquals("john@example.com", fromDb.getEmail());
    }

    // Edge case: update user keeping same email should work
    @Test
    @DisplayName("Integration: Update user keeping same email should succeed")
    void updateUser_WithSameEmail_ShouldSucceed() throws Exception {
        UserResponse created = userService.createUser(userRequest);

        // Update name but keep same email
        UserRequest sameEmailRequest = UserRequest.builder()
                .firstName("Johnny")
                .lastName("Doe")
                .email("john@example.com")  // same email
                .phone("9876543210")
                .role("ADMIN")
                .build();

        mockMvc.perform(put("/api/users/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameEmailRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
