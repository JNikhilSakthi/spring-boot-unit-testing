package com.ecommerce.unittesting.repository;

import com.ecommerce.unittesting.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// UserRepository — data access layer for User entity
//
// Return type conventions:
//   Optional<User> → when expecting 0 or 1 result (findById, findByEmail)
//   List<User>     → when expecting 0 or many results (findByRole)
//   boolean        → for existence checks (existsByEmail)
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Optional<User> because email is unique — returns 0 or 1 result
    // Optional prevents NullPointerException:
    //   repo.findByEmail("x").orElseThrow(() -> new NotFoundException(...))
    Optional<User> findByEmail(String email);

    // SELECT * FROM users WHERE role = ?
    List<User> findByRole(String role);

    // SELECT * FROM users WHERE LOWER(first_name) LIKE LOWER('%?%')
    List<User> findByFirstNameContainingIgnoreCase(String firstName);

    // SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
    // Returns true/false — used to check before creating (prevent duplicates)
    boolean existsByEmail(String email);

    // SELECT EXISTS(SELECT 1 FROM users WHERE email = ? AND id != ?)
    // Used during UPDATE to check if ANOTHER user already has this email
    // Excludes the current user's ID so updating with same email doesn't fail
    boolean existsByEmailAndIdNot(String email, Long id);
}
