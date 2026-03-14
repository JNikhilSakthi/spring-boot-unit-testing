package com.ecommerce.unittesting.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@XmlRootElement(name = "PaymentResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentResponse {

    @XmlElement
    private String transactionId;

    @XmlElement
    private String status;

    @XmlElement
    private String message;

    @XmlElement
    private double originalAmount;

    @XmlElement
    private String currency;

    @XmlElement
    private double exchangeRate;

    @XmlElement
    private double convertedAmount;

    @XmlElement
    private double processingFee;

    @XmlElement
    private double discount;

    @XmlElement
    private double tax;

    @XmlElement
    private double finalAmount;

    @XmlElement
    private String cardType;

    @XmlElement
    private String maskedCardNumber;

    @XmlElement
    private String fraudStatus;

    @XmlElement
    private int riskScore;

    @XmlElement
    private String bankReferenceId;

    @XmlElement
    private String processedAt;
}
