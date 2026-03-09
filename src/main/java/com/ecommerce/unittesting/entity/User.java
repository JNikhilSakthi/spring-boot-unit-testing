package com.ecommerce.unittesting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// @Entity     → JPA entity — maps to "users" table in database
// @Table      → Explicit table name (without this, JPA would use "user" which is a reserved keyword in some DBs)
// @Builder    → Enables: User.builder().firstName("John").lastName("Doe").build()
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JPA maps camelCase to snake_case automatically:
    // firstName → first_name column in DB
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // unique = true → DB creates a UNIQUE INDEX on this column
    // Prevents duplicate emails at the database level
    // If violated, throws DataIntegrityViolationException
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    // Role as String for simplicity (in real apps, use an Enum or separate Role entity)
    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // JPA Lifecycle Callbacks — automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
