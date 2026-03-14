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
@XmlRootElement(name = "PaymentRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentRequest {

    @XmlElement(required = true)
    private String cardNumber;

    @XmlElement(required = true)
    private String cardHolderName;

    @XmlElement(required = true)
    private int expiryMonth;

    @XmlElement(required = true)
    private int expiryYear;

    @XmlElement(required = true)
    private String cvv;

    @XmlElement(required = true)
    private double amount;

    @XmlElement(required = true)
    private String currency;

    @XmlElement(required = true)
    private String merchantId;

    @XmlElement
    private boolean loyaltyMember;

    @XmlElement
    private String description;
}
