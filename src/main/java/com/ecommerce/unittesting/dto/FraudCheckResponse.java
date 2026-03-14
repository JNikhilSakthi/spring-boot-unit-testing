package com.ecommerce.unittesting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCheckResponse {

    private int riskScore;
    private String status;
    private String message;
}
