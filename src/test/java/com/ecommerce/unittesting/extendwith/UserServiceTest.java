package com.ecommerce.unittesting.extendwith;

import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.exception.DuplicateResourceException;
import com.ecommerce.unittesting.exception.ResourceInUseException;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.ProductRepository;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @ExtendWith(MockitoExtension.class) — Pure unit test for UserService.
 * No Spring context. No database. Just Mockito mocks.
 *
 * TWO MOCK DEPENDENCIES now:
 *   @Mock UserRepository    → for user CRUD
 *   @Mock ProductRepository → for checking product dependencies before delete
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserRequest userRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        userRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();
    }

    // ==================== CREATE ====================

    @Test
    @DisplayName("Should create user successfully when email is unique")
    void createUser_WhenEmailUnique_ShouldReturnSavedUser() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.createUser(userRequest);

        assertNotNull(response);
        assertEquals("John", response.getFirstName());
        assertEquals("john@example.com", response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email already exists")
    void createUser_WhenEmailExists_ShouldThrowException() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.createUser(userRequest)
        );

        assertEquals("User already exists with email: john@example.com", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== GET BY ID ====================

    @Test
    @DisplayName("Should return user when found by ID")
    void getUserById_WhenExists_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John", response.getFirstName());
    }

    @Test
    @DisplayName("Should throw exception when user not found by ID")
    void getUserById_WhenNotExists_ShouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserById(99L));
    }

    // ==================== GET BY EMAIL ====================

    @Test
    @DisplayName("Should return user when found by email")
    void getUserByEmail_WhenExists_ShouldReturnUser() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserByEmail("john@example.com");

        assertEquals("john@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw exception when user not found by email")
    void getUserByEmail_WhenNotExists_ShouldThrowException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserByEmail("unknown@example.com"));
    }

    // ==================== GET ALL ====================

    @Test
    @DisplayName("Should return all users")
    void getAllUsers_ShouldReturnUserList() {
        User user2 = User.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("1234567890")
                .role("ADMIN")
                .build();

        when(userRepository.findAll()).thenReturn(Arrays.asList(user, user2));

        List<UserResponse> users = userService.getAllUsers();

        assertEquals(2, users.size());
        assertEquals("John", users.get(0).getFirstName());
        assertEquals("Jane", users.get(1).getFirstName());
    }

    @Test
    @DisplayName("Should return empty list when no users")
    void getAllUsers_WhenEmpty_ShouldReturnEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<UserResponse> users = userService.getAllUsers();

        assertTrue(users.isEmpty());
    }

    // ==================== GET BY ROLE ====================

    @Test
    @DisplayName("Should return users by role")
    void getUsersByRole_ShouldReturnFilteredUsers() {
        when(userRepository.findByRole("CUSTOMER")).thenReturn(List.of(user));

        List<UserResponse> users = userService.getUsersByRole("CUSTOMER");

        assertEquals(1, users.size());
        assertEquals("CUSTOMER", users.get(0).getRole());
    }

    // ==================== SEARCH ====================

    @Test
    @DisplayName("Should search users by first name")
    void searchUsers_ShouldReturnMatchingUsers() {
        when(userRepository.findByFirstNameContainingIgnoreCase("John")).thenReturn(List.of(user));

        List<UserResponse> users = userService.searchUsers("John");

        assertEquals(1, users.size());
        assertEquals("John", users.get(0).getFirstName());
    }

    // NEW: Edge case — empty search string should still call repository
    @Test
    @DisplayName("Should return all users when searching with empty string")
    void searchUsers_WithEmptyString_ShouldReturnResults() {
        when(userRepository.findByFirstNameContainingIgnoreCase("")).thenReturn(List.of(user));

        List<UserResponse> users = userService.searchUsers("");

        assertEquals(1, users.size());
        verify(userRepository).findByFirstNameContainingIgnoreCase("");
    }

    // ==================== UPDATE ====================

    @Test
    @DisplayName("Should update user successfully")
    void updateUser_WhenExists_ShouldReturnUpdatedUser() {
        UserRequest updateRequest = UserRequest.builder()
                .firstName("Johnny")
                .lastName("Doe Updated")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("ADMIN")
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .firstName("Johnny")
                .lastName("Doe Updated")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("ADMIN")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("johnny@example.com", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserResponse response = userService.updateUser(1L, updateRequest);

        assertEquals("Johnny", response.getFirstName());
        assertEquals("ADMIN", response.getRole());
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void updateUser_WhenNotExists_ShouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUser(99L, userRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    // NEW: Edge case — update user keeping same email should work
    @Test
    @DisplayName("Should allow update with same email (no conflict with self)")
    void updateUser_WithSameEmail_ShouldSucceed() {
        // User keeps their own email "john@example.com"
        UserRequest sameEmailRequest = UserRequest.builder()
                .firstName("Johnny")
                .lastName("Doe")
                .email("john@example.com")  // same email as current
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .firstName("Johnny")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // existsByEmailAndIdNot returns false because the SAME user has this email
        when(userRepository.existsByEmailAndIdNot("john@example.com", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserResponse response = userService.updateUser(1L, sameEmailRequest);

        assertEquals("Johnny", response.getFirstName());
        assertEquals("john@example.com", response.getEmail());
        verify(userRepository).save(any(User.class));
    }

    // NEW: Bug fix test — update user with another user's email should throw exception
    @Test
    @DisplayName("Should throw DuplicateResourceException when updating to another user's email")
    void updateUser_WithAnotherUsersEmail_ShouldThrowException() {
        UserRequest conflictRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("jane@example.com")  // belongs to another user
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // Another user already has this email
        when(userRepository.existsByEmailAndIdNot("jane@example.com", 1L)).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> userService.updateUser(1L, conflictRequest)
        );

        assertEquals("Email already in use: jane@example.com", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("Should delete user successfully when no products")
    void deleteUser_WhenExists_ShouldDeleteSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.existsByCreatedByIdOrUpdatedById(1L, 1L)).thenReturn(false);
        doNothing().when(userRepository).delete(user);

        assertDoesNotThrow(() -> userService.deleteUser(1L));

        verify(userRepository, times(1)).delete(user);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void deleteUser_WhenNotExists_ShouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.deleteUser(99L));

        verify(userRepository, never()).delete(any(User.class));
    }

    // NEW: Bug fix test — delete user who has products should throw ResourceInUseException
    @Test
    @DisplayName("Should throw ResourceInUseException when user has associated products")
    void deleteUser_WhenHasProducts_ShouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // User has products associated
        when(productRepository.existsByCreatedByIdOrUpdatedById(1L, 1L)).thenReturn(true);

        ResourceInUseException exception = assertThrows(
                ResourceInUseException.class,
                () -> userService.deleteUser(1L)
        );

        assertTrue(exception.getMessage().contains("Cannot delete user"));
        assertTrue(exception.getMessage().contains("associated products"));
        verify(userRepository, never()).delete(any(User.class));
    }
}
