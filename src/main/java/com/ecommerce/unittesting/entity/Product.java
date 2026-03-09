package com.ecommerce.unittesting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @Entity       → Tells JPA this class maps to a database table
// @Table        → Specifies the table name in DB (default would be "product")
// @Getter/@Setter → Lombok generates all getters/setters automatically
// @NoArgsConstructor → Lombok generates empty constructor (required by JPA)
// @AllArgsConstructor → Lombok generates constructor with all fields
// @Builder      → Lombok generates builder pattern: Product.builder().name("x").build()
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    // @Id                → Marks this field as the primary key
    // @GeneratedValue    → DB auto-generates the ID (1, 2, 3...)
    // GenerationType.IDENTITY → Uses DB's auto-increment (MySQL: AUTO_INCREMENT)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column(nullable = false) → This column cannot be NULL in DB
    // Maps to: VARCHAR(255) NOT NULL in MySQL
    @Column(nullable = false)
    private String name;

    // No @Column annotation → uses default settings (nullable = true)
    private String description;

    // BigDecimal is used for money/prices to avoid floating point errors
    // NEVER use double/float for money!
    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String category;

    // @ManyToOne → Many products can be created by ONE user
    // fetch = FetchType.LAZY → User data is loaded ONLY when accessed (not eagerly)
    //   LAZY  = SELECT * FROM products (user loaded later if needed)
    //   EAGER = SELECT * FROM products JOIN users (always joins — slower)
    // @JoinColumn → Creates a foreign key column "created_by" pointing to users.id
    // nullable = false → Every product MUST have a creator
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    // updatable = false → This column is set ONCE during INSERT, never updated
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // @PrePersist → JPA lifecycle callback — runs automatically BEFORE inserting into DB
    // So createdAt/updatedAt are set automatically without manual code
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // @PreUpdate → JPA lifecycle callback — runs automatically BEFORE updating in DB
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
