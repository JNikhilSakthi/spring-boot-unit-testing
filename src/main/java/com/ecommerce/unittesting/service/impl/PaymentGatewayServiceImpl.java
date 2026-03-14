package com.ecommerce.unittesting.service.impl;

import com.ecommerce.unittesting.dto.BankAuthResponse;
import com.ecommerce.unittesting.dto.FraudCheckResponse;
import com.ecommerce.unittesting.dto.PaymentRequest;
import com.ecommerce.unittesting.dto.PaymentResponse;
import com.ecommerce.unittesting.exception.*;
import com.ecommerce.unittesting.service.PaymentGatewayService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

    private final WebClient fraudServiceClient;
    private final WebClient bankServiceClient;

    private static final List<String> SUPPORTED_CURRENCIES = List.of(
            "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD"
    );

    private static final Map<String, Double> EXCHANGE_RATES = Map.of(
            "USD", 1.0,
            "EUR", 1.08,
            "GBP", 1.27,
            "INR", 0.012,
            "JPY", 0.0067,
            "AUD", 0.65,
            "CAD", 0.74
    );

    private static final double MAX_TRANSACTION_AMOUNT = 100000.0;
    private static final double FRAUD_CHECK_THRESHOLD = 1000.0;
    private static final int HIGH_RISK_SCORE = 80;
    private static final int MEDIUM_RISK_SCORE = 50;

    public PaymentGatewayServiceImpl(
            @Qualifier("fraudServiceClient") WebClient fraudServiceClient,
            @Qualifier("bankServiceClient") WebClient bankServiceClient) {
        this.fraudServiceClient = fraudServiceClient;
        this.bankServiceClient = bankServiceClient;
    }

    @Override
    public String processPayment(String xmlRequest) {

        // ======================== STEP 1: UNMARSHAL XML ========================
        PaymentRequest paymentRequest;
        try {
            JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            StringReader reader = new StringReader(xmlRequest);
            paymentRequest = (PaymentRequest) unmarshaller.unmarshal(reader);
        } catch (JAXBException e) {
            throw new XmlProcessingException("Failed to parse XML request: " + e.getMessage());
        }

        // ======================== STEP 2: VALIDATE INPUTS ========================
        validateCardNumber(paymentRequest.getCardNumber());
        validateExpiryDate(paymentRequest.getExpiryMonth(), paymentRequest.getExpiryYear());
        validateCVV(paymentRequest.getCvv(), paymentRequest.getCardNumber());
        validateAmount(paymentRequest.getAmount());
        validateCurrency(paymentRequest.getCurrency());

        // ======================== STEP 3: DETERMINE CARD TYPE ========================
        String cardType = determineCardType(paymentRequest.getCardNumber());

        // ======================== STEP 4: FRAUD CHECK ========================
        String fraudStatus = "CLEAR";
        int riskScore = 0;

        if (paymentRequest.getAmount() > FRAUD_CHECK_THRESHOLD) {
            try {
                FraudCheckResponse fraudResponse = fraudServiceClient.post()
                        .uri("/api/fraud/check")
                        .bodyValue(Map.of(
                                "cardNumber", maskCardNumber(paymentRequest.getCardNumber()),
                                "amount", paymentRequest.getAmount(),
                                "currency", paymentRequest.getCurrency()
                        ))
                        .retrieve()
                        .bodyToMono(FraudCheckResponse.class)
                        .block();

                if (fraudResponse != null) {
                    riskScore = fraudResponse.getRiskScore();

                    if (riskScore > HIGH_RISK_SCORE) {
                        throw new FraudDetectedException(
                                "Transaction flagged as fraudulent. Risk score: " + riskScore);
                    } else if (riskScore > MEDIUM_RISK_SCORE) {
                        fraudStatus = "MANUAL_REVIEW";
                    } else {
                        fraudStatus = "CLEAR";
                    }
                }
            } catch (FraudDetectedException e) {
                throw e;
            } catch (WebClientResponseException e) {
                throw new FraudServiceUnavailableException(
                        "Fraud detection service returned error: " + e.getStatusCode());
            } catch (Exception e) {
                throw new FraudServiceUnavailableException(
                        "Fraud detection service is unavailable: " + e.getMessage());
            }
        }

        // ======================== STEP 5: CALCULATE PROCESSING FEE ========================
        double processingFee = calculateProcessingFee(paymentRequest.getAmount(), cardType);

        // ======================== STEP 6: CURRENCY CONVERSION ========================
        double convertedAmount = paymentRequest.getAmount();
        double exchangeRate = 1.0;

        if (!"USD".equalsIgnoreCase(paymentRequest.getCurrency())) {
            exchangeRate = getExchangeRate(paymentRequest.getCurrency());
            convertedAmount = roundToTwoDecimals(paymentRequest.getAmount() * exchangeRate);
        }

        // ======================== STEP 7: LOYALTY DISCOUNT ========================
        double discount = 0.0;

        if (paymentRequest.isLoyaltyMember()) {
            if (convertedAmount > 5000) {
                discount = roundToTwoDecimals(convertedAmount * 0.05);
            } else if (convertedAmount > 2000) {
                discount = roundToTwoDecimals(convertedAmount * 0.03);
            } else {
                discount = roundToTwoDecimals(convertedAmount * 0.01);
            }
        }

        // ======================== STEP 8: CALCULATE TAX ========================
        double tax = roundToTwoDecimals((convertedAmount - discount) * 0.02);

        // ======================== STEP 9: FINAL AMOUNT ========================
        double finalAmount = roundToTwoDecimals(convertedAmount + processingFee - discount + tax);

        // ======================== STEP 10: BANK AUTHORIZATION ========================
        String transactionId;
        String bankReferenceId;

        try {
            BankAuthResponse bankResponse = bankServiceClient.post()
                    .uri("/api/bank/authorize")
                    .bodyValue(Map.of(
                            "cardNumber", paymentRequest.getCardNumber(),
                            "amount", finalAmount,
                            "currency", "USD",
                            "merchantId", paymentRequest.getMerchantId()
                    ))
                    .retrieve()
                    .bodyToMono(BankAuthResponse.class)
                    .block();

            if (bankResponse == null) {
                throw new GatewayTimeoutException("No response received from bank authorization service");
            }

            if ("DECLINED".equalsIgnoreCase(bankResponse.getStatus())) {
                if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(bankResponse.getDeclineReason())) {
                    throw new InsufficientFundsException(
                            "Insufficient funds for transaction amount: " + finalAmount);
                }
                throw new PaymentDeclinedException(
                        "Payment declined by bank: " + bankResponse.getDeclineReason());
            }

            if (!"APPROVED".equalsIgnoreCase(bankResponse.getStatus())) {
                throw new PaymentDeclinedException(
                        "Unexpected bank response status: " + bankResponse.getStatus());
            }

            transactionId = bankResponse.getTransactionId();
            bankReferenceId = bankResponse.getReferenceId();

        } catch (PaymentDeclinedException | InsufficientFundsException | GatewayTimeoutException e) {
            throw e;
        } catch (WebClientResponseException.GatewayTimeout e) {
            throw new GatewayTimeoutException("Bank authorization service timed out");
        } catch (WebClientResponseException e) {
            throw new PaymentDeclinedException(
                    "Bank service error: " + e.getStatusCode());
        } catch (Exception e) {
            throw new GatewayTimeoutException("Bank authorization failed: " + e.getMessage());
        }

        // ======================== STEP 11: BUILD RESPONSE ========================
        PaymentResponse response = PaymentResponse.builder()
                .transactionId(transactionId)
                .status("SUCCESS")
                .message("Payment processed successfully")
                .originalAmount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .exchangeRate(exchangeRate)
                .convertedAmount(convertedAmount)
                .processingFee(processingFee)
                .discount(discount)
                .tax(tax)
                .finalAmount(finalAmount)
                .cardType(cardType)
                .maskedCardNumber(maskCardNumber(paymentRequest.getCardNumber()))
                .fraudStatus(fraudStatus)
                .riskScore(riskScore)
                .bankReferenceId(bankReferenceId)
                .processedAt(LocalDateTime.now().toString())
                .build();

        // ======================== STEP 12: MARSHAL TO XML ========================
        try {
            JAXBContext context = JAXBContext.newInstance(PaymentResponse.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter writer = new StringWriter();
            marshaller.marshal(response, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new XmlProcessingException("Failed to generate XML response: " + e.getMessage());
        }
    }

    // ======================== VALIDATION METHODS ========================

    private void validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new InvalidCardException("Card number is required");
        }

        String cleaned = cardNumber.replaceAll("\\s+", "");

        if (!cleaned.matches("\\d{13,19}")) {
            throw new InvalidCardException("Card number must be 13-19 digits");
        }

        if (!isValidLuhn(cleaned)) {
            throw new InvalidCardException("Card number failed Luhn validation");
        }
    }

    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }

    private void validateExpiryDate(int month, int year) {
        if (month < 1 || month > 12) {
            throw new CardExpiredException("Invalid expiry month: " + month);
        }

        YearMonth expiry = YearMonth.of(year, month);
        YearMonth now = YearMonth.now();

        if (expiry.isBefore(now)) {
            throw new CardExpiredException("Card has expired: " + String.format("%02d/%d", month, year));
        }

        YearMonth maxFuture = now.plusYears(10);
        if (expiry.isAfter(maxFuture)) {
            throw new CardExpiredException(
                    "Expiry date is too far in the future: " + String.format("%02d/%d", month, year));
        }
    }

    private void validateCVV(String cvv, String cardNumber) {
        if (cvv == null || cvv.isBlank()) {
            throw new InvalidCVVException("CVV is required");
        }

        String cleanedCard = cardNumber.replaceAll("\\s+", "");
        boolean isAmex = cleanedCard.startsWith("34") || cleanedCard.startsWith("37");

        if (isAmex) {
            if (!cvv.matches("\\d{4}")) {
                throw new InvalidCVVException("AMEX cards require a 4-digit CVV");
            }
        } else {
            if (!cvv.matches("\\d{3}")) {
                throw new InvalidCVVException("CVV must be 3 digits");
            }
        }
    }

    private void validateAmount(double amount) {
        if (amount <= 0) {
            throw new AmountLimitExceededException("Payment amount must be greater than zero");
        }

        if (amount > MAX_TRANSACTION_AMOUNT) {
            throw new AmountLimitExceededException(
                    "Payment amount exceeds maximum limit of " + MAX_TRANSACTION_AMOUNT);
        }

        BigDecimal bd = BigDecimal.valueOf(amount);
        if (bd.scale() > 2) {
            throw new AmountLimitExceededException(
                    "Payment amount cannot have more than 2 decimal places");
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new UnsupportedCurrencyException("Currency is required");
        }

        if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new UnsupportedCurrencyException(
                    "Currency '" + currency + "' is not supported. Supported: " + SUPPORTED_CURRENCIES);
        }
    }

    // ======================== HELPER METHODS ========================

    private String determineCardType(String cardNumber) {
        String cleaned = cardNumber.replaceAll("\\s+", "");

        if (cleaned.startsWith("4")) {
            return "VISA";
        } else if (cleaned.startsWith("5")) {
            return "MASTERCARD";
        } else if (cleaned.startsWith("34") || cleaned.startsWith("37")) {
            return "AMEX";
        } else if (cleaned.startsWith("6")) {
            return "DISCOVER";
        } else {
            return "UNKNOWN";
        }
    }

    private double calculateProcessingFee(double amount, String cardType) {
        double feePercentage = switch (cardType) {
            case "VISA" -> 0.020;
            case "MASTERCARD" -> 0.022;
            case "AMEX" -> 0.025;
            case "DISCOVER" -> 0.018;
            default -> 0.030;
        };

        return roundToTwoDecimals(amount * feePercentage);
    }

    private double getExchangeRate(String currency) {
        // validateCurrency() already ensures only supported currencies reach here
        return EXCHANGE_RATES.get(currency.toUpperCase());
    }

    public String maskCardNumber(String cardNumber) {
        String cleaned = cardNumber.replaceAll("\\s+", "");
        if (cleaned.length() < 4) {
            return "****";
        }
        return "****" + cleaned.substring(cleaned.length() - 4);
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
