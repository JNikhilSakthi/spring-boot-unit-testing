package com.ecommerce.unittesting.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

// DTO (Data Transfer Object) — carries data between Controller and Service
// This is the INPUT DTO — what the client sends in the request body (JSON → Java)
//
// WHY use DTOs instead of Entity directly?
//   1. Entity has DB fields (id, createdAt) that the client should NOT send
//   2. Entity might expose internal fields you don't want in the API
//   3. Validation annotations belong on DTOs, not on entities
//   4. Decouples your API contract from your DB schema
//
// Flow: Client sends JSON → Spring converts to ProductRequest → Controller receives it
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    // @NotBlank → field cannot be null, empty (""), or whitespace ("   ")
    // Only works on String fields
    // message = custom error message returned in 400 response
    @NotBlank(message = "Product name is required")
    private String name;

    // No validation → optional field (can be null)
    private String description;

    // @NotNull → field cannot be null (but CAN be 0 — use @DecimalMin to prevent that)
    // @DecimalMin → minimum value check for BigDecimal/double fields
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    // @Min → minimum value check for integer fields
    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    @NotBlank(message = "Category is required")
    private String category;

    // The user who is creating/updating this product
    // Service will look up this user from UserRepository
    @NotNull(message = "User ID is required")
    private Long userId;
}
