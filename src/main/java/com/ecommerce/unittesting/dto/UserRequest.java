package com.ecommerce.unittesting.dto;

import jakarta.validation.constraints.*;
import lombok.*;

// INPUT DTO for User creation/update
// Validation annotations trigger when @Valid is used in the controller
//
// How validation works:
//   1. Client sends JSON → Spring converts to UserRequest
//   2. @Valid on controller parameter triggers validation
//   3. If any field fails → MethodArgumentNotValidException is thrown
//   4. GlobalExceptionHandler catches it → returns 400 with error details
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {

    // @NotBlank = @NotNull + @NotEmpty + trims whitespace
    // "  " (whitespace only) → FAILS
    // ""  (empty)            → FAILS
    // null                   → FAILS
    // "John"                 → PASSES
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    // @Email → validates email format (must have @ and domain)
    // "john@example.com"  → PASSES
    // "not-an-email"      → FAILS
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Role is required")
    private String role;
}
