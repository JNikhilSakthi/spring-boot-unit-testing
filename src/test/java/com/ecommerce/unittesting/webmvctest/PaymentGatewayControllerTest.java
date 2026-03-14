package com.ecommerce.unittesting.webmvctest;

import com.ecommerce.unittesting.controller.PaymentGatewayController;
import com.ecommerce.unittesting.dto.PaymentRequest;
import com.ecommerce.unittesting.exception.*;
import com.ecommerce.unittesting.service.PaymentGatewayService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentGatewayController.class)
@DisplayName("PaymentGatewayController WebMvcTest")
class PaymentGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentGatewayService paymentGatewayService;

    private static final String PAYMENT_URL = "/api/payment/process";

    // ======================== HELPER METHODS ========================

    private String buildPaymentXml() {
        try {
            PaymentRequest request = PaymentRequest.builder()
                    .cardNumber("4111111111111111")
                    .cardHolderName("John Doe")
                    .expiryMonth(12)
                    .expiryYear(2028)
                    .cvv("123")
                    .amount(500.0)
                    .currency("USD")
                    .merchantId("MERCH001")
                    .loyaltyMember(false)
                    .description("Test payment")
                    .build();

            JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter writer = new StringWriter();
            marshaller.marshal(request, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test XML", e);
        }
    }

    private String buildSuccessResponseXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <PaymentResponse>
                    <transactionId>TXN-123456</transactionId>
                    <status>SUCCESS</status>
                    <message>Payment processed successfully</message>
                    <originalAmount>500.0</originalAmount>
                    <currency>USD</currency>
                    <finalAmount>520.0</finalAmount>
                    <cardType>VISA</cardType>
                    <maskedCardNumber>****1111</maskedCardNumber>
                </PaymentResponse>
                """;
    }

    // ======================== SUCCESS TESTS ========================

    @Test
    @DisplayName("POST /api/payment/process → 200 OK with XML response")
    void processPayment_success() throws Exception {
        String requestXml = buildPaymentXml();
        String responseXml = buildSuccessResponseXml();

        when(paymentGatewayService.processPayment(anyString())).thenReturn(responseXml);

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(responseXml));
    }

    // ======================== VALIDATION ERROR TESTS (400) ========================

    @Test
    @DisplayName("POST /api/payment/process → 400 when XML is invalid")
    void processPayment_invalidXml_returns400() throws Exception {
        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new XmlProcessingException("Failed to parse XML request"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<invalid>xml</invalid>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("XML Processing Error"))
                .andExpect(jsonPath("$.message").value("Failed to parse XML request"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 400 when card number is invalid")
    void processPayment_invalidCard_returns400() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new InvalidCardException("Card number failed Luhn validation"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Card"))
                .andExpect(jsonPath("$.message").value("Card number failed Luhn validation"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 400 when card is expired")
    void processPayment_cardExpired_returns400() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new CardExpiredException("Card has expired: 01/2020"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Card Expired"))
                .andExpect(jsonPath("$.message").value("Card has expired: 01/2020"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 400 when CVV is invalid")
    void processPayment_invalidCVV_returns400() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new InvalidCVVException("CVV must be 3 digits"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid CVV"))
                .andExpect(jsonPath("$.message").value("CVV must be 3 digits"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 400 when currency is unsupported")
    void processPayment_unsupportedCurrency_returns400() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new UnsupportedCurrencyException("Currency 'BTC' is not supported"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported Currency"))
                .andExpect(jsonPath("$.message").value("Currency 'BTC' is not supported"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 400 when amount exceeds limit")
    void processPayment_amountLimitExceeded_returns400() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new AmountLimitExceededException("Payment amount exceeds maximum limit of 100000.0"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Amount Limit Exceeded"))
                .andExpect(jsonPath("$.message").value("Payment amount exceeds maximum limit of 100000.0"));
    }

    // ======================== FRAUD DETECTION TESTS (403, 503) ========================

    @Test
    @DisplayName("POST /api/payment/process → 403 when fraud detected")
    void processPayment_fraudDetected_returns403() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new FraudDetectedException("Transaction flagged as fraudulent. Risk score: 90"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Fraud Detected"))
                .andExpect(jsonPath("$.message").value("Transaction flagged as fraudulent. Risk score: 90"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 503 when fraud service is unavailable")
    void processPayment_fraudServiceUnavailable_returns503() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new FraudServiceUnavailableException("Fraud detection service is unavailable"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Fraud Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Fraud detection service is unavailable"));
    }

    // ======================== BANK AUTHORIZATION TESTS (422, 504) ========================

    @Test
    @DisplayName("POST /api/payment/process → 422 when payment is declined")
    void processPayment_paymentDeclined_returns422() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new PaymentDeclinedException("Payment declined by bank: CARD_BLOCKED"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Payment Declined"))
                .andExpect(jsonPath("$.message").value("Payment declined by bank: CARD_BLOCKED"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 422 when insufficient funds")
    void processPayment_insufficientFunds_returns422() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new InsufficientFundsException("Insufficient funds for transaction amount: 520.0"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"))
                .andExpect(jsonPath("$.message").value("Insufficient funds for transaction amount: 520.0"));
    }

    @Test
    @DisplayName("POST /api/payment/process → 504 when gateway times out")
    void processPayment_gatewayTimeout_returns504() throws Exception {
        String requestXml = buildPaymentXml();

        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new GatewayTimeoutException("Bank authorization service timed out"));

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("Gateway Timeout"))
                .andExpect(jsonPath("$.message").value("Bank authorization service timed out"));
    }
}
