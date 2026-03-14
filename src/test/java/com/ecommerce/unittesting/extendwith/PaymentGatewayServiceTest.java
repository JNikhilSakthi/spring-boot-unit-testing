package com.ecommerce.unittesting.extendwith;

import com.ecommerce.unittesting.dto.BankAuthResponse;
import com.ecommerce.unittesting.dto.FraudCheckResponse;
import com.ecommerce.unittesting.dto.PaymentRequest;
import com.ecommerce.unittesting.exception.*;
import com.ecommerce.unittesting.service.impl.PaymentGatewayServiceImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGatewayService Unit Tests")
class PaymentGatewayServiceTest {

    @Mock
    private WebClient fraudServiceClient;

    @Mock
    private WebClient bankServiceClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private PaymentGatewayServiceImpl paymentGatewayService;

    @BeforeEach
    void setUp() {
        paymentGatewayService = new PaymentGatewayServiceImpl(fraudServiceClient, bankServiceClient);
    }

    // ======================== HELPER METHODS ========================

    private String buildPaymentXml(String cardNumber, String cardHolderName, int expiryMonth, int expiryYear,
                                   String cvv, double amount, String currency, String merchantId,
                                   boolean loyaltyMember, String description) {
        try {
            PaymentRequest request = PaymentRequest.builder()
                    .cardNumber(cardNumber)
                    .cardHolderName(cardHolderName)
                    .expiryMonth(expiryMonth)
                    .expiryYear(expiryYear)
                    .cvv(cvv)
                    .amount(amount)
                    .currency(currency)
                    .merchantId(merchantId)
                    .loyaltyMember(loyaltyMember)
                    .description(description)
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

    private String buildValidPaymentXml(double amount, String currency, boolean loyaltyMember) {
        return buildPaymentXml("4111111111111111", "John Doe", 12, 2028,
                "123", amount, currency, "MERCH001", loyaltyMember, "Test payment");
    }

    private String buildValidPaymentXml() {
        return buildValidPaymentXml(500.0, "USD", false);
    }

    private void mockBankApproval() {
        when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.just(
                BankAuthResponse.builder()
                        .transactionId("TXN-123456")
                        .referenceId("BNK-789")
                        .status("APPROVED")
                        .build()
        ));
    }

    private void mockFraudCheck(int riskScore, String status) {
        when(fraudServiceClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(FraudCheckResponse.class)).thenReturn(Mono.just(
                FraudCheckResponse.builder()
                        .riskScore(riskScore)
                        .status(status)
                        .message("Fraud check completed")
                        .build()
        ));
    }

    private void mockFraudCheckThenBankApproval(int riskScore, String fraudStatus) {
        // First call → fraud service, second call → bank service
        when(fraudServiceClient.post()).thenReturn(requestBodyUriSpec);
        when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        when(responseSpec.bodyToMono(FraudCheckResponse.class)).thenReturn(Mono.just(
                FraudCheckResponse.builder()
                        .riskScore(riskScore)
                        .status(fraudStatus)
                        .message("Fraud check completed")
                        .build()
        ));
        when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.just(
                BankAuthResponse.builder()
                        .transactionId("TXN-123456")
                        .referenceId("BNK-789")
                        .status("APPROVED")
                        .build()
        ));
    }

    // ======================== HAPPY PATH TESTS ========================

    @Nested
    @DisplayName("Happy Path - Successful Payments")
    class HappyPathTests {

        @Test
        @DisplayName("Should process USD payment successfully (amount < 1000, no fraud check)")
        void processPayment_successUSD_noFraudCheck() {
            String xml = buildValidPaymentXml(500.0, "USD", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<transactionId>TXN-123456</transactionId>");
            assertThat(result).contains("<cardType>VISA</cardType>");
            assertThat(result).contains("<maskedCardNumber>****1111</maskedCardNumber>");
            assertThat(result).contains("<fraudStatus>CLEAR</fraudStatus>");
            assertThat(result).contains("<originalAmount>500.0</originalAmount>");
            verify(fraudServiceClient, never()).post();
        }

        @Test
        @DisplayName("Should process EUR payment with currency conversion")
        void processPayment_successEUR_withConversion() {
            String xml = buildValidPaymentXml(500.0, "EUR", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<currency>EUR</currency>");
            assertThat(result).contains("<exchangeRate>1.08</exchangeRate>");
            assertThat(result).contains("<convertedAmount>540.0</convertedAmount>");
        }

        @Test
        @DisplayName("Should process GBP payment with currency conversion")
        void processPayment_successGBP_withConversion() {
            String xml = buildValidPaymentXml(100.0, "GBP", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<exchangeRate>1.27</exchangeRate>");
        }

        @Test
        @DisplayName("Should process INR payment with currency conversion")
        void processPayment_successINR_withConversion() {
            String xml = buildValidPaymentXml(500.0, "INR", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<exchangeRate>0.012</exchangeRate>");
        }

        @Test
        @DisplayName("Should process payment with amount > 1000 and clear fraud check")
        void processPayment_amountOver1000_fraudCheckClear() {
            String xml = buildValidPaymentXml(1500.0, "USD", false);
            mockFraudCheckThenBankApproval(20, "CLEAR");

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<fraudStatus>CLEAR</fraudStatus>");
            assertThat(result).contains("<riskScore>20</riskScore>");
        }
    }

    // ======================== LOYALTY DISCOUNT TESTS ========================

    @Nested
    @DisplayName("Loyalty Discount Calculations")
    class LoyaltyDiscountTests {

        @Test
        @DisplayName("Should apply 5% discount for loyalty member with amount > 5000")
        void processPayment_loyaltyDiscount_high_5percent() {
            String xml = buildValidPaymentXml(6000.0, "USD", true);
            mockFraudCheckThenBankApproval(10, "CLEAR");

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            // 5% of 6000 = 300.0
            assertThat(result).contains("<discount>300.0</discount>");
        }

        @Test
        @DisplayName("Should apply 3% discount for loyalty member with amount > 2000")
        void processPayment_loyaltyDiscount_medium_3percent() {
            String xml = buildValidPaymentXml(3000.0, "USD", true);
            mockFraudCheckThenBankApproval(10, "CLEAR");

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            // 3% of 3000 = 90.0
            assertThat(result).contains("<discount>90.0</discount>");
        }

        @Test
        @DisplayName("Should apply 1% discount for loyalty member with amount <= 2000")
        void processPayment_loyaltyDiscount_low_1percent() {
            String xml = buildValidPaymentXml(500.0, "USD", true);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            // 1% of 500 = 5.0
            assertThat(result).contains("<discount>5.0</discount>");
        }

        @Test
        @DisplayName("Should not apply discount for non-loyalty member")
        void processPayment_noDiscount_notLoyaltyMember() {
            String xml = buildValidPaymentXml(5000.0, "USD", false);
            mockFraudCheckThenBankApproval(10, "CLEAR");

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<discount>0.0</discount>");
        }
    }

    // ======================== CARD TYPE TESTS ========================

    @Nested
    @DisplayName("Card Type Detection & Processing Fees")
    class CardTypeTests {

        @Test
        @DisplayName("Should detect VISA card (starts with 4) and charge 2.0% fee")
        void processPayment_visaCard_2percentFee() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 12, 2028,
                    "123", 1000.0, "USD", "MERCH001", false, "Test");
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<cardType>VISA</cardType>");
            // 2.0% of 1000 = 20.0
            assertThat(result).contains("<processingFee>20.0</processingFee>");
        }

        @Test
        @DisplayName("Should detect MASTERCARD (starts with 5) and charge 2.2% fee")
        void processPayment_mastercardCard_2point2percentFee() {
            String xml = buildPaymentXml("5500000000000004", "Jane Doe", 12, 2028,
                    "123", 1000.0, "USD", "MERCH001", false, "Test");
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<cardType>MASTERCARD</cardType>");
            // 2.2% of 1000 = 22.0
            assertThat(result).contains("<processingFee>22.0</processingFee>");
        }

        @Test
        @DisplayName("Should detect AMEX card (starts with 37) and charge 2.5% fee, require 4-digit CVV")
        void processPayment_amexCard_2point5percentFee() {
            String xml = buildPaymentXml("371449635398431", "Jane Doe", 12, 2028,
                    "1234", 1000.0, "USD", "MERCH001", false, "Test");
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<cardType>AMEX</cardType>");
            // 2.5% of 1000 = 25.0
            assertThat(result).contains("<processingFee>25.0</processingFee>");
        }

        @Test
        @DisplayName("Should detect DISCOVER card (starts with 6) and charge 1.8% fee")
        void processPayment_discoverCard_1point8percentFee() {
            String xml = buildPaymentXml("6011111111111117", "Jake Doe", 12, 2028,
                    "123", 1000.0, "USD", "MERCH001", false, "Test");
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<cardType>DISCOVER</cardType>");
            // 1.8% of 1000 = 18.0
            assertThat(result).contains("<processingFee>18.0</processingFee>");
        }
    }

    // ======================== CARD VALIDATION TESTS ========================

    @Nested
    @DisplayName("Card Number Validation")
    class CardValidationTests {

        @Test
        @DisplayName("Should throw InvalidCardException for null card number")
        void processPayment_nullCardNumber() {
            String xml = buildPaymentXml(null, "John Doe", 12, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCardException.class)
                    .hasMessageContaining("Card number is required");
        }

        @Test
        @DisplayName("Should throw InvalidCardException for card number with letters")
        void processPayment_cardNumberWithLetters() {
            String xml = buildPaymentXml("4111ABCD11111111", "John Doe", 12, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCardException.class)
                    .hasMessageContaining("13-19 digits");
        }

        @Test
        @DisplayName("Should throw InvalidCardException for card number failing Luhn check")
        void processPayment_cardNumberFailsLuhn() {
            String xml = buildPaymentXml("4111111111111112", "John Doe", 12, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCardException.class)
                    .hasMessageContaining("Luhn validation");
        }

        @Test
        @DisplayName("Should throw InvalidCardException for too short card number")
        void processPayment_cardNumberTooShort() {
            String xml = buildPaymentXml("411111", "John Doe", 12, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCardException.class)
                    .hasMessageContaining("13-19 digits");
        }
    }

    // ======================== EXPIRY DATE TESTS ========================

    @Nested
    @DisplayName("Expiry Date Validation")
    class ExpiryDateTests {

        @Test
        @DisplayName("Should throw CardExpiredException for expired card")
        void processPayment_expiredCard() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 1, 2020,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(CardExpiredException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should throw CardExpiredException for expiry date too far in future (> 10 years)")
        void processPayment_expiryTooFarInFuture() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 12, 2040,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(CardExpiredException.class)
                    .hasMessageContaining("too far in the future");
        }

        @Test
        @DisplayName("Should throw CardExpiredException for invalid month (0)")
        void processPayment_invalidExpiryMonth_zero() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 0, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(CardExpiredException.class)
                    .hasMessageContaining("Invalid expiry month");
        }

        @Test
        @DisplayName("Should throw CardExpiredException for invalid month (13)")
        void processPayment_invalidExpiryMonth_thirteen() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 13, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(CardExpiredException.class)
                    .hasMessageContaining("Invalid expiry month");
        }
    }

    // ======================== CVV VALIDATION TESTS ========================

    @Nested
    @DisplayName("CVV Validation")
    class CVVValidationTests {

        @Test
        @DisplayName("Should throw InvalidCVVException for null CVV")
        void processPayment_nullCVV() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 12, 2028,
                    null, 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCVVException.class)
                    .hasMessageContaining("CVV is required");
        }

        @Test
        @DisplayName("Should throw InvalidCVVException for 2-digit CVV on VISA card")
        void processPayment_shortCVV_visa() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 12, 2028,
                    "12", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCVVException.class)
                    .hasMessageContaining("3 digits");
        }

        @Test
        @DisplayName("Should throw InvalidCVVException for 3-digit CVV on AMEX card")
        void processPayment_threedigitCVV_amex() {
            String xml = buildPaymentXml("371449635398431", "John Doe", 12, 2028,
                    "123", 500.0, "USD", "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InvalidCVVException.class)
                    .hasMessageContaining("4-digit CVV");
        }
    }

    // ======================== AMOUNT VALIDATION TESTS ========================

    @Nested
    @DisplayName("Amount Validation")
    class AmountValidationTests {

        @Test
        @DisplayName("Should throw AmountLimitExceededException for negative amount")
        void processPayment_negativeAmount() {
            String xml = buildValidPaymentXml(-100.0, "USD", false);

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(AmountLimitExceededException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Should throw AmountLimitExceededException for zero amount")
        void processPayment_zeroAmount() {
            String xml = buildValidPaymentXml(0.0, "USD", false);

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(AmountLimitExceededException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Should throw AmountLimitExceededException for amount > 100000")
        void processPayment_amountExceedsLimit() {
            String xml = buildValidPaymentXml(150000.0, "USD", false);

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(AmountLimitExceededException.class)
                    .hasMessageContaining("maximum limit");
        }

        @Test
        @DisplayName("Should throw AmountLimitExceededException for more than 2 decimal places")
        void processPayment_tooManyDecimalPlaces() {
            String xml = buildValidPaymentXml(100.123, "USD", false);

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(AmountLimitExceededException.class)
                    .hasMessageContaining("2 decimal places");
        }
    }

    // ======================== CURRENCY VALIDATION TESTS ========================

    @Nested
    @DisplayName("Currency Validation")
    class CurrencyValidationTests {

        @Test
        @DisplayName("Should throw UnsupportedCurrencyException for unsupported currency")
        void processPayment_unsupportedCurrency() {
            String xml = buildValidPaymentXml(500.0, "BTC", false);

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(UnsupportedCurrencyException.class)
                    .hasMessageContaining("not supported");
        }

        @Test
        @DisplayName("Should throw UnsupportedCurrencyException for null currency")
        void processPayment_nullCurrency() {
            String xml = buildPaymentXml("4111111111111111", "John Doe", 12, 2028,
                    "123", 500.0, null, "MERCH001", false, "Test");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(UnsupportedCurrencyException.class)
                    .hasMessageContaining("required");
        }
    }

    // ======================== XML PROCESSING TESTS ========================

    @Nested
    @DisplayName("XML Processing")
    class XmlProcessingTests {

        @Test
        @DisplayName("Should throw XmlProcessingException for invalid XML")
        void processPayment_invalidXml() {
            String invalidXml = "<InvalidTag>not a payment</InvalidTag>";

            assertThatThrownBy(() -> paymentGatewayService.processPayment(invalidXml))
                    .isInstanceOf(XmlProcessingException.class)
                    .hasMessageContaining("Failed to parse");
        }

        @Test
        @DisplayName("Should throw XmlProcessingException for malformed XML")
        void processPayment_malformedXml() {
            String malformedXml = "this is not xml at all <<<>>>";

            assertThatThrownBy(() -> paymentGatewayService.processPayment(malformedXml))
                    .isInstanceOf(XmlProcessingException.class)
                    .hasMessageContaining("Failed to parse");
        }

        @Test
        @DisplayName("Should throw XmlProcessingException for empty XML string")
        void processPayment_emptyXml() {
            assertThatThrownBy(() -> paymentGatewayService.processPayment(""))
                    .isInstanceOf(XmlProcessingException.class)
                    .hasMessageContaining("Failed to parse");
        }
    }

    // ======================== FRAUD CHECK TESTS ========================

    @Nested
    @DisplayName("Fraud Detection Service")
    class FraudCheckTests {

        @Test
        @DisplayName("Should skip fraud check when amount <= 1000")
        void processPayment_amountUnder1000_skipsFraudCheck() {
            String xml = buildValidPaymentXml(999.0, "USD", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<fraudStatus>CLEAR</fraudStatus>");
            assertThat(result).contains("<riskScore>0</riskScore>");
            verify(fraudServiceClient, never()).post();
        }

        @Test
        @DisplayName("Should set MANUAL_REVIEW status when risk score is between 50-80")
        void processPayment_fraudCheck_manualReview() {
            String xml = buildValidPaymentXml(2000.0, "USD", false);
            mockFraudCheckThenBankApproval(65, "SUSPICIOUS");

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<status>SUCCESS</status>");
            assertThat(result).contains("<fraudStatus>MANUAL_REVIEW</fraudStatus>");
            assertThat(result).contains("<riskScore>65</riskScore>");
        }

        @Test
        @DisplayName("Should throw FraudDetectedException when risk score > 80")
        void processPayment_fraudCheck_highRisk() {
            String xml = buildValidPaymentXml(5000.0, "USD", false);
            mockFraudCheck(90, "FRAUDULENT");

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(FraudDetectedException.class)
                    .hasMessageContaining("Risk score: 90");
        }

        @Test
        @DisplayName("Should throw FraudServiceUnavailableException when fraud service returns error")
        void processPayment_fraudService_webClientError() {
            String xml = buildValidPaymentXml(5000.0, "USD", false);

            when(fraudServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(FraudCheckResponse.class))
                    .thenReturn(Mono.error(WebClientResponseException.create(
                            503, "Service Unavailable", null, null, null)));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(FraudServiceUnavailableException.class);
        }

        @Test
        @DisplayName("Should throw FraudServiceUnavailableException when fraud service throws generic exception")
        void processPayment_fraudService_genericError() {
            String xml = buildValidPaymentXml(5000.0, "USD", false);

            when(fraudServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(FraudCheckResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Connection refused")));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(FraudServiceUnavailableException.class)
                    .hasMessageContaining("Connection refused");
        }
    }

    // ======================== BANK AUTHORIZATION TESTS ========================

    @Nested
    @DisplayName("Bank Authorization Service")
    class BankAuthTests {

        @Test
        @DisplayName("Should throw PaymentDeclinedException when bank declines payment")
        void processPayment_bankDeclined() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.just(
                    BankAuthResponse.builder()
                            .status("DECLINED")
                            .declineReason("CARD_BLOCKED")
                            .build()
            ));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(PaymentDeclinedException.class)
                    .hasMessageContaining("CARD_BLOCKED");
        }

        @Test
        @DisplayName("Should throw InsufficientFundsException when bank reports insufficient funds")
        void processPayment_insufficientFunds() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.just(
                    BankAuthResponse.builder()
                            .status("DECLINED")
                            .declineReason("INSUFFICIENT_FUNDS")
                            .build()
            ));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("Should throw GatewayTimeoutException when bank response is null")
        void processPayment_bankResponse_null() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.empty());

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(GatewayTimeoutException.class)
                    .hasMessageContaining("No response");
        }

        @Test
        @DisplayName("Should throw PaymentDeclinedException for unexpected bank status")
        void processPayment_bankResponse_unexpectedStatus() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class)).thenReturn(Mono.just(
                    BankAuthResponse.builder()
                            .status("PENDING")
                            .build()
            ));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(PaymentDeclinedException.class)
                    .hasMessageContaining("Unexpected bank response status");
        }

        @Test
        @DisplayName("Should throw GatewayTimeoutException when bank service times out")
        void processPayment_bankService_timeout() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class))
                    .thenReturn(Mono.error(WebClientResponseException.create(
                            504, "Gateway Timeout", null, null, null)));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(GatewayTimeoutException.class);
        }

        @Test
        @DisplayName("Should throw PaymentDeclinedException when bank service returns HTTP error")
        void processPayment_bankService_httpError() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class))
                    .thenReturn(Mono.error(WebClientResponseException.create(
                            500, "Internal Server Error", null, null, null)));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(PaymentDeclinedException.class)
                    .hasMessageContaining("Bank service error");
        }

        @Test
        @DisplayName("Should throw GatewayTimeoutException when bank service throws generic exception")
        void processPayment_bankService_genericError() {
            String xml = buildValidPaymentXml(500.0, "USD", false);

            when(bankServiceClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(BankAuthResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Connection reset")));

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(GatewayTimeoutException.class)
                    .hasMessageContaining("Connection reset");
        }
    }

    // ======================== CALCULATION VERIFICATION TESTS ========================

    @Nested
    @DisplayName("Calculation Verification")
    class CalculationTests {

        @Test
        @DisplayName("Should calculate correct final amount: amount + fee - discount + tax")
        void processPayment_correctFinalAmount_withDiscount() {
            // amount=1000 USD, VISA (2% fee=20), loyalty (1% discount=10), tax=2% of (1000-10)=19.80
            // final = 1000 + 20 - 10 + 19.80 = 1029.80
            String xml = buildValidPaymentXml(1000.0, "USD", true);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<processingFee>20.0</processingFee>");
            assertThat(result).contains("<discount>10.0</discount>");
            assertThat(result).contains("<tax>19.8</tax>");
            assertThat(result).contains("<finalAmount>1029.8</finalAmount>");
        }

        @Test
        @DisplayName("Should calculate correct final amount without discount")
        void processPayment_correctFinalAmount_noDiscount() {
            // amount=500 USD, VISA (2% fee=10), no loyalty, tax=2% of 500=10
            // final = 500 + 10 - 0 + 10 = 520
            String xml = buildValidPaymentXml(500.0, "USD", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<processingFee>10.0</processingFee>");
            assertThat(result).contains("<discount>0.0</discount>");
            assertThat(result).contains("<tax>10.0</tax>");
            assertThat(result).contains("<finalAmount>520.0</finalAmount>");
        }

        @Test
        @DisplayName("Should calculate correctly with EUR currency conversion")
        void processPayment_correctCalculation_eurConversion() {
            // amount=100 EUR, rate=1.08, converted=108 USD, VISA (2% fee=2), no loyalty, tax=2% of 108=2.16
            // final = 108 + 2 - 0 + 2.16 = 112.16
            String xml = buildValidPaymentXml(100.0, "EUR", false);
            mockBankApproval();

            String result = paymentGatewayService.processPayment(xml);

            assertThat(result).contains("<convertedAmount>108.0</convertedAmount>");
            assertThat(result).contains("<processingFee>2.0</processingFee>");
            assertThat(result).contains("<tax>2.16</tax>");
            assertThat(result).contains("<finalAmount>112.16</finalAmount>");
        }
    }

    // ======================== MASK CARD NUMBER TESTS ========================

    @Nested
    @DisplayName("Card Number Masking")
    class MaskCardNumberTests {

        @Test
        @DisplayName("Should mask card number showing only last 4 digits")
        void maskCardNumber_shouldShowLastFourDigits() {
            String masked = paymentGatewayService.maskCardNumber("4111111111111111");
            assertThat(masked).isEqualTo("****1111");
        }

        @Test
        @DisplayName("Should handle short card number gracefully")
        void maskCardNumber_shortNumber() {
            String masked = paymentGatewayService.maskCardNumber("12");
            assertThat(masked).isEqualTo("****");
        }
    }
}
