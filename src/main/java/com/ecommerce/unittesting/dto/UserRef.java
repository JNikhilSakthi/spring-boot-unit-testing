package com.ecommerce.unittesting.dto;

import lombok.*;

// UserRef — lightweight reference to a User
// Used inside ProductResponse to show WHO created/updated the product
//
// WHY not use the full UserResponse?
//   - UserResponse has email, phone, role — too much info for a product response
//   - UserRef has only id + username — just enough to identify the user
//   - This is a common pattern: "embedded reference" vs "full object"
//
// JSON output example:
//   "createdBy": {
//       "id": 1,
//       "username": "John Doe"
//   }
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRef {

    private Long id;
    private String username;
}
