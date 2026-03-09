package com.ecommerce.unittesting.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// OUTPUT DTO — what the server sends back to the client (Java → JSON)
//
// WHY different from ProductRequest?
//   - Response has id, createdAt, updatedAt (server-generated, not from client)
//   - Response has UserRef (nested object) instead of just userId
//   - Request has userId (Long), Response has full UserRef (id + username)
//
// Flow: Service creates this → Controller returns it → Spring converts to JSON
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer quantity;
    private String category;

    // UserRef instead of plain String — gives client both id and username
    // JSON output: "createdBy": { "id": 1, "username": "John Doe" }
    private UserRef createdBy;
    private UserRef updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
