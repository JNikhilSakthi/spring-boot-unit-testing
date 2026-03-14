package com.ecommerce.unittesting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAuthResponse {

    private String transactionId;
    private String referenceId;
    private String status;
    private String declineReason;
}
