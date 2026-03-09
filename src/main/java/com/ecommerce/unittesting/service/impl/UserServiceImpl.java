package com.ecommerce.unittesting.service.impl;

import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;
import com.ecommerce.unittesting.entity.User;
import com.ecommerce.unittesting.exception.DuplicateResourceException;
import com.ecommerce.unittesting.exception.ResourceInUseException;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.repository.ProductRepository;
import com.ecommerce.unittesting.repository.UserRepository;
import com.ecommerce.unittesting.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// @Service → Business logic layer
// @RequiredArgsConstructor → Constructor injection for dependencies
//
// TWO DEPENDENCIES now:
//   UserRepository    → for user CRUD operations
//   ProductRepository → for checking if user has products before deletion
//
// In @ExtendWith tests: both are mocked with @Mock, injected via @InjectMocks
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // CREATE — with duplicate email check
    // This is business logic that MUST be tested:
    //   - Happy path: email is unique → create user
    //   - Error path: email exists → throw DuplicateResourceException
    @Override
    public UserResponse createUser(UserRequest request) {
        // Check for duplicate email BEFORE saving
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole())
                .build();

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getUsersByRole(String role) {
        return userRepository.findByRole(role)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> searchUsers(String firstName) {
        return userRepository.findByFirstNameContainingIgnoreCase(firstName)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // UPDATE — with duplicate email check for OTHER users
    // BUG FIX: Previously, updating a user's email to another user's email
    // would silently succeed, causing a unique constraint violation in DB.
    // Now we check: does ANOTHER user (id != current) have this email?
    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Check if another user already has this email (exclude current user)
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    // DELETE — with product dependency check
    // BUG FIX: Previously, deleting a user who created products would cause
    // an ugly 500 error (FK constraint violation). Now we check first and
    // return a meaningful 409 Conflict response.
    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Check if user has any associated products (createdBy or updatedBy)
        if (productRepository.existsByCreatedByIdOrUpdatedById(id, id)) {
            throw new ResourceInUseException("Cannot delete user with id: " + id + " — user has associated products");
        }

        userRepository.delete(user);
    }

    // MAPPER — Entity → DTO
    // Collects all entity fields and sets them to the response DTO
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
