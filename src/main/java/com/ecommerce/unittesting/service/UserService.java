package com.ecommerce.unittesting.service;

import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;

import java.util.List;

// Service Interface for User domain
// Same pattern as ProductService — interface + implementation
public interface UserService {

    UserResponse createUser(UserRequest request);

    UserResponse getUserById(Long id);

    UserResponse getUserByEmail(String email);

    List<UserResponse> getAllUsers();

    List<UserResponse> getUsersByRole(String role);

    List<UserResponse> searchUsers(String firstName);

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);
}
