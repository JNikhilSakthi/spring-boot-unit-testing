package com.ecommerce.unittesting.dto;

import lombok.*;

import java.time.LocalDateTime;

// OUTPUT DTO — sent back to client
// Contains server-generated fields (id, createdAt, updatedAt) that the client never sends
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
