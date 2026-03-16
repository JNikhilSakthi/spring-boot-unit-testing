# Payment Gateway — Every Line Explained

> This document explains every single line of the Payment Gateway feature.
> After reading this, you should understand WHY each line exists and HOW it connects to the tests.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [File Map — What Each File Does](#2-file-map)
3. [PaymentRequest DTO — XML Input](#3-paymentrequest-dto)
4. [PaymentResponse DTO — XML Output](#4-paymentresponse-dto)
5. [FraudCheckResponse & BankAuthResponse — External API DTOs](#5-external-api-dtos)
6. [WebClientConfig — External Service Configuration](#6-webclientconfig)
7. [PaymentGatewayService Interface](#7-service-interface)
8. [PaymentGatewayServiceImpl — The Core Logic (Line by Line)](#8-service-impl)
9. [PaymentGatewayController — The REST Endpoint](#9-controller)
10. [GlobalExceptionHandler — Error Response Mapping](#10-exception-handler)
11. [Custom Exception Classes](#11-exception-classes)
12. [PaymentGatewayServiceTest — Service Unit Tests (Line by Line)](#12-service-test)
13. [PaymentGatewayControllerTest — Controller Tests (Line by Line)](#13-controller-test)

---

## 1. Architecture Overview

```
Client sends XML request
        │
        ▼
┌─────────────────────────────┐
│  PaymentGatewayController   │  ← Receives XML via POST /api/payment/process
│  (REST endpoint)            │  ← Returns XML response
└──────────┬──────────────────┘
           │ calls
           ▼
┌─────────────────────────────┐
│  PaymentGatewayServiceImpl  │  ← ALL business logic lives here
│                             │
│  Step 1:  Unmarshal XML     │  ← JAXB: XML string → PaymentRequest object
│  Step 2:  Validate inputs   │  ← Card number, expiry, CVV, amount, currency
│  Step 3:  Card type detect  │  ← VISA, MASTERCARD, AMEX, DISCOVER, UNKNOWN
│  Step 4:  Fraud check       │  ← WebClient → external fraud service (if amount > $1000)
│  Step 5:  Processing fee    │  ← Based on card type (VISA 2%, AMEX 2.5%, etc.)
│  Step 6:  Currency convert  │  ← Non-USD → USD using exchange rates
│  Step 7:  Loyalty discount  │  ← 1% / 3% / 5% based on converted amount
│  Step 8:  Calculate tax     │  ← 2% of (convertedAmount - discount)
│  Step 9:  Final amount      │  ← converted + fee - discount + tax
│  Step 10: Bank auth         │  ← WebClient → external bank service
│  Step 11: Build response    │  ← PaymentResponse object with all calculated fields
│  Step 12: Marshal XML       │  ← JAXB: PaymentResponse object → XML string
└──────────┬──────────────────┘
           │                          ┌─────────────────────┐
           ├──── WebClient POST ────► │  Fraud Service      │  (external, mocked in tests)
           │                          │  /api/fraud/check   │
           │                          └─────────────────────┘
           │                          ┌─────────────────────┐
           ├──── WebClient POST ────► │  Bank Service       │  (external, mocked in tests)
           │                          │  /api/bank/authorize│
           │                          └─────────────────────┘
           │
           ▼
┌─────────────────────────────┐
│  GlobalExceptionHandler     │  ← Catches all exceptions, returns proper HTTP codes
│  12 @ExceptionHandler       │  ← Each exception → specific status code + JSON error
└─────────────────────────────┘
```

**Key design decisions:**
- **XML in, XML out**: Uses JAXB for marshalling/unmarshalling (real-world payment gateways often use XML)
- **No database**: This is a processing gateway, not storage — it talks to external services
- **Two external calls**: Fraud check (optional) + Bank authorization (always)
- **Rich validation**: 5 validation methods catch bad input before any external call

---

## 2. File Map

```
src/main/java/com/ecommerce/unittesting/
├── config/
│   └── WebClientConfig.java          ← Creates two WebClient beans (fraud + bank)
├── controller/
│   └── PaymentGatewayController.java ← POST /api/payment/process (XML in, XML out)
├── dto/
│   ├── PaymentRequest.java           ← XML input (card, amount, currency, etc.)
│   ├── PaymentResponse.java          ← XML output (transactionId, status, fees, etc.)
│   ├── FraudCheckResponse.java       ← JSON from fraud service (riskScore, status)
│   └── BankAuthResponse.java         ← JSON from bank service (transactionId, status)
├── exception/
│   ├── GlobalExceptionHandler.java   ← Maps exceptions → HTTP error responses
│   ├── InvalidCardException.java     ← Card number validation failures → 400
│   ├── CardExpiredException.java     ← Expiry date failures → 400
│   ├── InvalidCVVException.java      ← CVV validation failures → 400
│   ├── AmountLimitExceededException.java ← Amount validation failures → 400
│   ├── UnsupportedCurrencyException.java ← Currency validation failures → 400
│   ├── XmlProcessingException.java   ← JAXB marshal/unmarshal failures → 400
│   ├── FraudDetectedException.java   ← High risk score → 403
│   ├── FraudServiceUnavailableException.java ← Fraud service down → 503
│   ├── PaymentDeclinedException.java ← Bank declined → 422
│   ├── InsufficientFundsException.java ← Not enough money → 422
│   └── GatewayTimeoutException.java  ← Bank unreachable → 504
├── service/
│   ├── PaymentGatewayService.java    ← Interface (1 method)
│   └── impl/
│       └── PaymentGatewayServiceImpl.java ← ALL business logic (390 lines)
│
src/test/java/com/ecommerce/unittesting/
├── extendwith/
│   └── PaymentGatewayServiceTest.java    ← Unit test (62 tests, @ExtendWith)
└── webmvctest/
    └── PaymentGatewayControllerTest.java ← Controller test (12 tests, @WebMvcTest)
```

---

## 3. PaymentRequest DTO — XML Input

```java
@Data                                          // Lombok: generates getters, setters, toString, equals, hashCode
@NoArgsConstructor                             // Lombok: generates empty constructor (JAXB requires this)
@AllArgsConstructor                            // Lombok: generates constructor with all fields
@Builder                                       // Lombok: enables PaymentRequest.builder().cardNumber("...").build()
@XmlRootElement(name = "PaymentRequest")       // JAXB: this class maps to <PaymentRequest> XML tag
@XmlAccessorType(XmlAccessType.FIELD)          // JAXB: map fields directly (not getters) to XML elements
public class PaymentRequest {

    @XmlElement(required = true)               // JAXB: <cardNumber> tag, must be present
    private String cardNumber;                 // "4111111111111111" — the full card number

    @XmlElement(required = true)
    private String cardHolderName;             // "John Doe"

    @XmlElement(required = true)
    private int expiryMonth;                   // 1-12

    @XmlElement(required = true)
    private int expiryYear;                    // 2028

    @XmlElement(required = true)
    private String cvv;                        // "123" (3 digits) or "1234" (4 digits for AMEX)

    @XmlElement(required = true)
    private double amount;                     // 500.0 — payment amount in original currency

    @XmlElement(required = true)
    private String currency;                   // "USD", "EUR", "GBP", etc.

    @XmlElement(required = true)
    private String merchantId;                 // "MERCH001" — identifies the merchant

    @XmlElement
    private boolean loyaltyMember;             // true = eligible for discount

    @XmlElement
    private String description;                // "Test payment" — optional note
}
```

**Why XML?** Real payment gateways (Stripe XML API, bank SWIFT messages) often use XML. This teaches JAXB marshalling/unmarshalling which is a real-world skill.

**Why `@NoArgsConstructor`?** JAXB requires a no-arg constructor to create objects during unmarshalling. Without it, `unmarshaller.unmarshal()` throws an error.

**Why `@XmlAccessorType(XmlAccessType.FIELD)`?** Tells JAXB to look at fields directly instead of getters. Without this, JAXB might try to use both fields AND getters, causing duplicate XML elements.

---

## 4. PaymentResponse DTO — XML Output

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@XmlRootElement(name = "PaymentResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentResponse {

    private String transactionId;              // "TXN-123456" — from bank
    private String status;                     // "SUCCESS"
    private String message;                    // "Payment processed successfully"
    private double originalAmount;             // 500.0 — what user sent
    private String currency;                   // "EUR" — original currency
    private double exchangeRate;               // 1.08 — EUR to USD rate
    private double convertedAmount;            // 540.0 — after conversion to USD
    private double processingFee;              // 10.8 — card type fee
    private double discount;                   // 5.4 — loyalty discount
    private double tax;                        // 10.69 — 2% tax
    private double finalAmount;                // 556.09 — what gets charged
    private String cardType;                   // "VISA"
    private String maskedCardNumber;           // "****1111" — never expose full number
    private String fraudStatus;                // "CLEAR" / "MANUAL_REVIEW"
    private int riskScore;                     // 0-100
    private String bankReferenceId;            // "BNK-789" — bank's reference
    private String processedAt;                // "2026-03-16T10:30:00" — when processed
}
```

**Why so many fields?** Each field represents a calculation step. The response shows the client exactly how the final amount was computed — transparency in financial transactions.

---

## 5. External API DTOs

### FraudCheckResponse — What the fraud service returns

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudCheckResponse {
    private int riskScore;      // 0-100 (0 = safe, 100 = definitely fraud)
    private String status;      // "CLEAR", "SUSPICIOUS", "FRAUDULENT"
    private String message;     // "Fraud check completed"
}
```

### BankAuthResponse — What the bank returns

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAuthResponse {
    private String transactionId;   // Bank's transaction ID
    private String referenceId;     // Bank's internal reference
    private String status;          // "APPROVED" / "DECLINED"
    private String declineReason;   // "INSUFFICIENT_FUNDS" / "CARD_BLOCKED" / null if approved
}
```

**Why are these plain JSON (no @Xml annotations)?** The fraud and bank services return JSON (standard for REST APIs). Only the payment gateway's own input/output uses XML. WebClient handles JSON deserialization automatically.

---

## 6. WebClientConfig

```java
@Configuration                                  // Spring: this class provides bean definitions
public class WebClientConfig {

    @Value("${fraud.service.base-url:http://localhost:8081}")    // Read from application.yml, default if missing
    private String fraudServiceBaseUrl;

    @Value("${bank.service.base-url:http://localhost:8082}")
    private String bankServiceBaseUrl;

    @Bean(name = "fraudServiceClient")          // Spring: register as bean with specific name
    public WebClient fraudServiceClient() {     // Other classes inject this by @Qualifier("fraudServiceClient")
        return WebClient.builder()
                .baseUrl(fraudServiceBaseUrl)    // All requests use this as base URL
                .build();
    }

    @Bean(name = "bankServiceClient")
    public WebClient bankServiceClient() {
        return WebClient.builder()
                .baseUrl(bankServiceBaseUrl)
                .build();
    }
}
```

**Why two separate WebClient beans?** Each external service has a different base URL. Named beans let us inject the right one using `@Qualifier`.

**Why this matters for testing**: In unit tests, we DON'T use this config at all. We `@Mock WebClient fraudServiceClient` directly. This config only runs in the real application.

---

## 7. PaymentGatewayService Interface

```java
public interface PaymentGatewayService {
    String processPayment(String xmlRequest);   // XML in, XML out
}
```

**Why an interface?** Standard Spring practice — program to interfaces, not implementations. Allows:
- Easy mocking in controller tests (`@MockitoBean PaymentGatewayService`)
- Swapping implementations without changing callers
- Clear contract definition

---

## 8. PaymentGatewayServiceImpl — The Core Logic (Line by Line)

This is the most important file. Every line is explained.

### Class Declaration & Dependencies

```java
@Service                                        // Spring: register as a service bean
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

    private final WebClient fraudServiceClient;  // Injected — calls fraud detection API
    private final WebClient bankServiceClient;   // Injected — calls bank authorization API
```

**Why `final`?** Ensures dependencies can't be reassigned after construction. Combined with constructor injection, this makes the class immutable and thread-safe.

### Constants

```java
    private static final List<String> SUPPORTED_CURRENCIES = List.of(
            "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD"
    );
```
**Why `List.of()`?** Creates an immutable list. No one can accidentally add/remove currencies at runtime.

```java
    private static final Map<String, Double> EXCHANGE_RATES = Map.of(
            "USD", 1.0,     // Base currency — everything converts TO USD
            "EUR", 1.08,    // 1 EUR = 1.08 USD
            "GBP", 1.27,    // 1 GBP = 1.27 USD
            "INR", 0.012,   // 1 INR = 0.012 USD
            "JPY", 0.0067,  // 1 JPY = 0.0067 USD
            "AUD", 0.65,    // 1 AUD = 0.65 USD
            "CAD", 0.74     // 1 CAD = 0.74 USD
    );
```
**Coverage impact:** Each currency needs at least one test to cover the map lookup. Tests exist for USD, EUR, GBP, INR, JPY, AUD, CAD.

```java
    private static final double MAX_TRANSACTION_AMOUNT = 100000.0;   // Reject payments above this
    private static final double FRAUD_CHECK_THRESHOLD = 1000.0;      // Only check fraud if amount > 1000
    private static final int HIGH_RISK_SCORE = 80;                    // Risk > 80 = reject
    private static final int MEDIUM_RISK_SCORE = 50;                  // Risk 50-80 = manual review
```

### Constructor — Dependency Injection

```java
    public PaymentGatewayServiceImpl(
            @Qualifier("fraudServiceClient") WebClient fraudServiceClient,
            @Qualifier("bankServiceClient") WebClient bankServiceClient) {
        this.fraudServiceClient = fraudServiceClient;
        this.bankServiceClient = bankServiceClient;
    }
```
**Why `@Qualifier`?** There are two `WebClient` beans. Without `@Qualifier`, Spring doesn't know which one to inject. `@Qualifier("fraudServiceClient")` says "inject the bean named fraudServiceClient".

**In tests:** We bypass this entirely — `new PaymentGatewayServiceImpl(mockFraud, mockBank)` in `@BeforeEach`.

---

### STEP 1: Unmarshal XML → PaymentRequest Object

```java
    PaymentRequest paymentRequest;
    try {
        JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);
        // JAXBContext knows HOW to convert between PaymentRequest and XML.
        // It reads @XmlRootElement, @XmlElement annotations to know the mapping.

        Unmarshaller unmarshaller = context.createUnmarshaller();
        // Unmarshaller = XML → Java object converter

        StringReader reader = new StringReader(xmlRequest);
        // StringReader wraps the XML string so JAXB can read it

        paymentRequest = (PaymentRequest) unmarshaller.unmarshal(reader);
        // Actually parses the XML and creates a PaymentRequest object
        // If XML is invalid/malformed → throws JAXBException

    } catch (JAXBException e) {
        throw new XmlProcessingException("Failed to parse XML request: " + e.getMessage());
        // Convert checked JAXBException → unchecked XmlProcessingException
        // GlobalExceptionHandler catches this → returns 400 Bad Request
    }
```

**Coverage checklist for this block:**
- `[x]` Happy path: valid XML → parses successfully → moves to step 2
- `[x]` Catch path: invalid XML → JAXBException → XmlProcessingException

**Tests that cover this:**
- `processPayment_invalidXml()` — `"<InvalidTag>not a payment</InvalidTag>"` (wrong root element)
- `processPayment_malformedXml()` — `"this is not xml at all <<<>>>"` (not XML at all)
- `processPayment_emptyXml()` — `""` (empty string)

---

### STEP 2: Validate Inputs

```java
    validateCardNumber(paymentRequest.getCardNumber());
    validateExpiryDate(paymentRequest.getExpiryMonth(), paymentRequest.getExpiryYear());
    validateCVV(paymentRequest.getCvv(), paymentRequest.getCardNumber());
    validateAmount(paymentRequest.getAmount());
    validateCurrency(paymentRequest.getCurrency());
```

Each validate method throws a specific exception if invalid. If all pass, execution continues. **Order matters** — card is validated before CVV because CVV validation depends on card type (AMEX = 4 digits).

#### validateCardNumber (3 checks)

```java
    private void validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
        //  ^^^^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^^^^
        //  Check 1a: null         Check 1b: empty/"   "
        //  Both → "Card number is required"
            throw new InvalidCardException("Card number is required");
        }

        String cleaned = cardNumber.replaceAll("\\s+", "");
        // Remove spaces: "4111 1111 1111 1111" → "4111111111111111"

        if (!cleaned.matches("\\d{13,19}")) {
        //  Must be 13-19 digits only. No letters, no special chars.
        //  13 = minimum card number length (some old cards)
        //  19 = maximum card number length (some newer cards)
            throw new InvalidCardException("Card number must be 13-19 digits");
        }

        if (!isValidLuhn(cleaned)) {
        //  Luhn algorithm = checksum validation used by all major card networks
        //  Catches typos: "4111111111111112" fails (last digit wrong)
            throw new InvalidCardException("Card number failed Luhn validation");
        }
    }
```

**Coverage: 4 tests needed (1 per check + 1 happy path)**
- `[x]` null → "Card number is required"
- `[x]` blank `"   "` → "Card number is required"
- `[x]` letters `"4111ABCD..."` → "13-19 digits"
- `[x]` too short `"411111"` → "13-19 digits"
- `[x]` bad Luhn `"4111111111111112"` → "Luhn validation"
- `[x]` valid `"4111111111111111"` → passes (happy path tests)

#### isValidLuhn — The Luhn Algorithm

```java
    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        // Process digits RIGHT to LEFT

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            // Get numeric value of each character

            if (alternate) {
                digit *= 2;              // Double every second digit from right
                if (digit > 9) {
                    digit -= 9;          // If doubled value > 9, subtract 9
                }                        // (equivalent to adding the two digits: 16 → 1+6=7, same as 16-9=7)
            }

            sum += digit;
            alternate = !alternate;      // Toggle for next iteration
        }

        return sum % 10 == 0;            // Valid if total is divisible by 10
    }
```

**Coverage:** This is covered indirectly — valid cards pass Luhn, invalid cards fail it. The `if (digit > 9)` branch needs a card number where doubling produces > 9 (covered by `4111111111111111` which has digits that double to > 9).

#### validateExpiryDate (3 checks)

```java
    private void validateExpiryDate(int month, int year) {
        if (month < 1 || month > 12) {
        //  Month must be 1-12. Catches 0, 13, -1, etc.
            throw new CardExpiredException("Invalid expiry month: " + month);
        }

        YearMonth expiry = YearMonth.of(year, month);
        YearMonth now = YearMonth.now();

        if (expiry.isBefore(now)) {
        //  Card expired — expiry date is in the past
            throw new CardExpiredException("Card has expired: " + String.format("%02d/%d", month, year));
        }

        YearMonth maxFuture = now.plusYears(10);
        if (expiry.isAfter(maxFuture)) {
        //  Expiry too far in future — probably a typo or fake card
            throw new CardExpiredException("Expiry date is too far in the future: "...);
        }
    }
```

**Coverage: 4 tests**
- `[x]` month=0 → "Invalid expiry month: 0"
- `[x]` month=13 → "Invalid expiry month: 13"
- `[x]` past date (01/2020) → "Card has expired"
- `[x]` far future (12/2040) → "too far in the future"
- `[x]` valid (12/2028) → passes (happy path tests)

#### validateCVV (3 checks, depends on card type)

```java
    private void validateCVV(String cvv, String cardNumber) {
        if (cvv == null || cvv.isBlank()) {
            throw new InvalidCVVException("CVV is required");
        }

        String cleanedCard = cardNumber.replaceAll("\\s+", "");
        boolean isAmex = cleanedCard.startsWith("34") || cleanedCard.startsWith("37");
        // AMEX cards start with 34 or 37 — they use 4-digit CVV

        if (isAmex) {
            if (!cvv.matches("\\d{4}")) {
            //  AMEX requires exactly 4 digits
                throw new InvalidCVVException("AMEX cards require a 4-digit CVV");
            }
        } else {
            if (!cvv.matches("\\d{3}")) {
            //  All other cards require exactly 3 digits
                throw new InvalidCVVException("CVV must be 3 digits");
            }
        }
    }
```

**Coverage: 4 tests**
- `[x]` null CVV → "CVV is required"
- `[x]` blank CVV `"   "` → "CVV is required"
- `[x]` AMEX card + 3-digit CVV → "4-digit CVV"
- `[x]` VISA card + 2-digit CVV → "3 digits"
- `[x]` valid VISA + "123" → passes
- `[x]` valid AMEX + "1234" → passes

#### validateAmount (3 checks)

```java
    private void validateAmount(double amount) {
        if (amount <= 0) {
            throw new AmountLimitExceededException("Payment amount must be greater than zero");
        }

        if (amount > MAX_TRANSACTION_AMOUNT) {
        //  MAX_TRANSACTION_AMOUNT = 100000.0
            throw new AmountLimitExceededException("Payment amount exceeds maximum limit of " + MAX_TRANSACTION_AMOUNT);
        }

        BigDecimal bd = BigDecimal.valueOf(amount);
        if (bd.scale() > 2) {
        //  No more than 2 decimal places: 100.12 OK, 100.123 NOT OK
        //  Prevents precision issues in financial calculations
            throw new AmountLimitExceededException("Payment amount cannot have more than 2 decimal places");
        }
    }
```

**Coverage: 4 tests**
- `[x]` amount=-100 → "greater than zero"
- `[x]` amount=0 → "greater than zero"
- `[x]` amount=150000 → "maximum limit"
- `[x]` amount=100.123 → "2 decimal places"
- `[x]` amount=500.0 → passes

#### validateCurrency (2 checks)

```java
    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new UnsupportedCurrencyException("Currency is required");
        }

        if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new UnsupportedCurrencyException(
                    "Currency '" + currency + "' is not supported. Supported: " + SUPPORTED_CURRENCIES);
        }
    }
```

**Coverage: 3 tests**
- `[x]` null → "required"
- `[x]` blank `"   "` → "required"
- `[x]` "BTC" → "not supported"
- `[x]` "USD" → passes

---

### STEP 3: Determine Card Type

```java
    private String determineCardType(String cardNumber) {
        String cleaned = cardNumber.replaceAll("\\s+", "");

        if (cleaned.startsWith("4")) {                          // VISA starts with 4
            return "VISA";
        } else if (cleaned.startsWith("5")) {                   // MASTERCARD starts with 5
            return "MASTERCARD";
        } else if (cleaned.startsWith("34") || cleaned.startsWith("37")) {  // AMEX starts with 34 or 37
            return "AMEX";
        } else if (cleaned.startsWith("6")) {                   // DISCOVER starts with 6
            return "DISCOVER";
        } else {                                                 // Anything else
            return "UNKNOWN";
        }
    }
```

**Coverage: 5 branches = 5 tests needed**
- `[x]` "4111..." → VISA
- `[x]` "5500..." → MASTERCARD
- `[x]` "3714..." → AMEX (prefix 37)
- `[x]` "3400..." → AMEX (prefix 34)
- `[x]` "6011..." → DISCOVER
- `[x]` "9000..." → UNKNOWN

**Why test both AMEX prefixes?** The `||` has two conditions. For branch coverage, JaCoCo tracks each side separately. Testing only "37" means "34" path is untested.

---

### STEP 4: Fraud Check (Most Complex Block)

```java
    String fraudStatus = "CLEAR";       // Default: no fraud
    int riskScore = 0;                  // Default: zero risk

    if (paymentRequest.getAmount() > FRAUD_CHECK_THRESHOLD) {
    //  FRAUD_CHECK_THRESHOLD = 1000.0
    //  Only check fraud for large transactions (saves API calls for small payments)

        try {
            FraudCheckResponse fraudResponse = fraudServiceClient.post()
                    .uri("/api/fraud/check")               // POST to fraud service
                    .bodyValue(Map.of(                      // Send card + amount info
                            "cardNumber", maskCardNumber(paymentRequest.getCardNumber()),
                            "amount", paymentRequest.getAmount(),
                            "currency", paymentRequest.getCurrency()
                    ))
                    .retrieve()                             // Execute the request
                    .bodyToMono(FraudCheckResponse.class)   // Expect JSON → FraudCheckResponse
                    .block();                               // Block until response (synchronous)

            if (fraudResponse != null) {
            //  Null check: .block() can return null if Mono is empty
                riskScore = fraudResponse.getRiskScore();

                if (riskScore > HIGH_RISK_SCORE) {          // > 80 = definitely fraud
                    throw new FraudDetectedException(
                            "Transaction flagged as fraudulent. Risk score: " + riskScore);
                } else if (riskScore > MEDIUM_RISK_SCORE) { // 50-80 = suspicious
                    fraudStatus = "MANUAL_REVIEW";
                } else {                                     // < 50 = safe
                    fraudStatus = "CLEAR";
                }
            }
            // If fraudResponse is null: skip all inner logic, keep defaults (CLEAR, 0)

        } catch (FraudDetectedException e) {
            throw e;
            // RE-THROW: Don't swallow this — it must reach the controller as 403

        } catch (WebClientResponseException e) {
            throw new FraudServiceUnavailableException(
                    "Fraud detection service returned error: " + e.getStatusCode());
            // HTTP error from fraud service (503, 500, etc.)
            // Convert to our custom exception → GlobalExceptionHandler → 503 response

        } catch (Exception e) {
            throw new FraudServiceUnavailableException(
                    "Fraud detection service is unavailable: " + e.getMessage());
            // ANY other error (connection refused, DNS failure, timeout, etc.)
            // Broad catch as safety net
        }
    }
    // If amount <= 1000: entire block is skipped, fraudStatus stays "CLEAR", riskScore stays 0
```

**Why 3 catch blocks?**
1. `FraudDetectedException` — We threw this ourselves inside the try. We must re-throw it, NOT convert it to FraudServiceUnavailableException.
2. `WebClientResponseException` — The fraud service responded but with an error HTTP status (503, 500). We know the service is reachable but broken.
3. `Exception` — Catch-all for anything else: connection refused, DNS failure, timeout. We don't know what went wrong, but the fraud service is unavailable.

**Coverage: 7 tests needed for this block alone**

| # | Test | Branch covered |
|---|------|---------------|
| 1 | `amount=999` | `if (amount > 1000)` = FALSE → entire block skipped |
| 2 | `amount=1500, riskScore=20` | TRUE → not null → `> 80` false → `> 50` false → CLEAR |
| 3 | `amount=2000, riskScore=65` | TRUE → not null → `> 80` false → `> 50` true → MANUAL_REVIEW |
| 4 | `amount=5000, riskScore=90` | TRUE → not null → `> 80` true → FraudDetectedException |
| 5 | `amount=5000, null response` | TRUE → null → skip inner if → keep defaults |
| 6 | `amount=5000, WebClientResponseException` | TRUE → catch #2 → FraudServiceUnavailableException |
| 7 | `amount=5000, RuntimeException` | TRUE → catch #3 → FraudServiceUnavailableException |

---

### STEP 5: Processing Fee

```java
    private double calculateProcessingFee(double amount, String cardType) {
        double feePercentage = switch (cardType) {
            case "VISA" -> 0.020;         // 2.0%
            case "MASTERCARD" -> 0.022;   // 2.2%
            case "AMEX" -> 0.025;         // 2.5%
            case "DISCOVER" -> 0.018;     // 1.8%
            default -> 0.030;             // 3.0% for unknown cards
        };

        return roundToTwoDecimals(amount * feePercentage);
    }
```

**Coverage: 5 switch cases = 5 tests**
- `[x]` VISA → 2.0%
- `[x]` MASTERCARD → 2.2%
- `[x]` AMEX → 2.5%
- `[x]` DISCOVER → 1.8%
- `[x]` UNKNOWN → 3.0% (default)

---

### STEP 6: Currency Conversion

```java
    double convertedAmount = paymentRequest.getAmount();
    double exchangeRate = 1.0;

    if (!"USD".equalsIgnoreCase(paymentRequest.getCurrency())) {
    //  Only convert if NOT already USD
        exchangeRate = getExchangeRate(paymentRequest.getCurrency());
        convertedAmount = roundToTwoDecimals(paymentRequest.getAmount() * exchangeRate);
    }
```

**Coverage: 2 branches**
- `[x]` USD → skip conversion (exchangeRate stays 1.0)
- `[x]` EUR → convert (500 * 1.08 = 540.0)
- `[x]` GBP, INR, JPY, AUD, CAD → each tested with its own exchange rate

---

### STEP 7: Loyalty Discount

```java
    double discount = 0.0;

    if (paymentRequest.isLoyaltyMember()) {           // Outer: member or not
        if (convertedAmount > 5000) {                  // 5% discount for big spenders
            discount = roundToTwoDecimals(convertedAmount * 0.05);
        } else if (convertedAmount > 2000) {           // 3% discount for medium
            discount = roundToTwoDecimals(convertedAmount * 0.03);
        } else {                                       // 1% discount for everyone else
            discount = roundToTwoDecimals(convertedAmount * 0.01);
        }
    }
    // If not loyalty member: discount stays 0.0
```

**Coverage: 4 branches**
- `[x]` loyalty=false → discount=0
- `[x]` loyalty=true, amount=6000 → 5% = 300
- `[x]` loyalty=true, amount=3000 → 3% = 90
- `[x]` loyalty=true, amount=500 → 1% = 5

---

### STEP 8-9: Tax & Final Amount

```java
    double tax = roundToTwoDecimals((convertedAmount - discount) * 0.02);
    // 2% tax on (converted amount minus discount)

    double finalAmount = roundToTwoDecimals(convertedAmount + processingFee - discount + tax);
    // Final = converted + fee - discount + tax
```

**Coverage:** No branches here — these are pure calculations. Covered by calculation verification tests that assert exact values.

---

### STEP 10: Bank Authorization (Second Complex Block)

```java
    try {
        BankAuthResponse bankResponse = bankServiceClient.post()
                .uri("/api/bank/authorize")
                .bodyValue(Map.of(
                        "cardNumber", paymentRequest.getCardNumber(),
                        "amount", finalAmount,
                        "currency", "USD",                    // Always send USD to bank
                        "merchantId", paymentRequest.getMerchantId()
                ))
                .retrieve()
                .bodyToMono(BankAuthResponse.class)
                .block();

        if (bankResponse == null) {
            throw new GatewayTimeoutException("No response received from bank authorization service");
            // Mono.empty() → .block() returns null → no response from bank
        }

        if ("DECLINED".equalsIgnoreCase(bankResponse.getStatus())) {
            if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(bankResponse.getDeclineReason())) {
                throw new InsufficientFundsException(
                        "Insufficient funds for transaction amount: " + finalAmount);
                // Specific decline reason → specific exception → 422 with clear message
            }
            throw new PaymentDeclinedException(
                    "Payment declined by bank: " + bankResponse.getDeclineReason());
            // Generic decline (CARD_BLOCKED, FRAUD_SUSPECTED, etc.)
        }

        if (!"APPROVED".equalsIgnoreCase(bankResponse.getStatus())) {
            throw new PaymentDeclinedException(
                    "Unexpected bank response status: " + bankResponse.getStatus());
            // Not DECLINED, not APPROVED — something weird like "PENDING"
        }

        transactionId = bankResponse.getTransactionId();
        bankReferenceId = bankResponse.getReferenceId();

    } catch (PaymentDeclinedException | InsufficientFundsException | GatewayTimeoutException e) {
        throw e;
        // RE-THROW our own exceptions — don't convert them

    } catch (WebClientResponseException.GatewayTimeout e) {
        throw new GatewayTimeoutException("Bank authorization service timed out");
        // Specific: HTTP 504 from bank → our GatewayTimeoutException

    } catch (WebClientResponseException e) {
        throw new PaymentDeclinedException("Bank service error: " + e.getStatusCode());
        // Any other HTTP error from bank (500, 502, etc.)

    } catch (Exception e) {
        throw new GatewayTimeoutException("Bank authorization failed: " + e.getMessage());
        // Catch-all: connection refused, DNS failure, etc.
    }
```

**Coverage: 8 tests needed**

| # | Test | What it covers |
|---|------|---------------|
| 1 | APPROVED response | Happy path → get transactionId + referenceId |
| 2 | null response | `bankResponse == null` → GatewayTimeoutException |
| 3 | DECLINED + INSUFFICIENT_FUNDS | `DECLINED` → `INSUFFICIENT_FUNDS` → InsufficientFundsException |
| 4 | DECLINED + CARD_BLOCKED | `DECLINED` → other reason → PaymentDeclinedException |
| 5 | PENDING status | Not DECLINED, not APPROVED → PaymentDeclinedException |
| 6 | HTTP 504 | catch `WebClientResponseException.GatewayTimeout` → GatewayTimeoutException |
| 7 | HTTP 500 | catch `WebClientResponseException` → PaymentDeclinedException |
| 8 | RuntimeException | catch `Exception` → GatewayTimeoutException |

---

### STEP 11: Build Response

```java
    PaymentResponse response = PaymentResponse.builder()
            .transactionId(transactionId)                    // From bank
            .status("SUCCESS")                               // We made it past all checks
            .message("Payment processed successfully")
            .originalAmount(paymentRequest.getAmount())      // What user sent
            .currency(paymentRequest.getCurrency())          // Original currency
            .exchangeRate(exchangeRate)                      // 1.0 for USD, else actual rate
            .convertedAmount(convertedAmount)                // After conversion
            .processingFee(processingFee)                    // Card type fee
            .discount(discount)                              // Loyalty discount
            .tax(tax)                                        // 2% tax
            .finalAmount(finalAmount)                        // What gets charged
            .cardType(cardType)                              // VISA, MASTERCARD, etc.
            .maskedCardNumber(maskCardNumber(...))            // ****1111
            .fraudStatus(fraudStatus)                        // CLEAR or MANUAL_REVIEW
            .riskScore(riskScore)                            // 0-100
            .bankReferenceId(bankReferenceId)                // From bank
            .processedAt(LocalDateTime.now().toString())     // Timestamp
            .build();
```

**Coverage:** No branches — this runs on every successful path. Covered by any happy path test.

---

### STEP 12: Marshal Response → XML

```java
    try {
        JAXBContext context = JAXBContext.newInstance(PaymentResponse.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        // Pretty-print XML with indentation

        StringWriter writer = new StringWriter();
        marshaller.marshal(response, writer);
        return writer.toString();

    } catch (JAXBException e) {
        throw new XmlProcessingException("Failed to generate XML response: " + e.getMessage());
    }
```

**Coverage: 2 paths**
- `[x]` Happy path: every successful test covers this (XML is returned)
- `[x]` Catch path: `processPayment_marshalFailure()` uses `MockedStatic<JAXBContext>` to force JAXBException

**Why is the marshal catch hard to test?** In normal code, `PaymentResponse` is a valid JAXB class — marshalling will never fail. To test the catch block, we must use `MockedStatic` to make `JAXBContext.newInstance(PaymentResponse.class)` throw an exception. This is the technique for covering "unreachable" catch blocks.

---

### Helper: maskCardNumber

```java
    public String maskCardNumber(String cardNumber) {
        String cleaned = cardNumber.replaceAll("\\s+", "");
        if (cleaned.length() < 4) {
            return "****";                       // Very short number → just mask everything
        }
        return "****" + cleaned.substring(cleaned.length() - 4);
        // Show only last 4 digits: "4111111111111111" → "****1111"
    }
```

**Coverage: 2 branches**
- `[x]` length < 4 → "****"
- `[x]` normal length → "****1111"

---

### Helper: roundToTwoDecimals

```java
    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
```

**Why?** Floating point math is imprecise: `0.1 + 0.2 = 0.30000000000000004`. Financial calculations MUST use precise rounding. `HALF_UP` = standard banker's rounding.

**Coverage:** Called by every calculation — no dedicated test needed, covered by calculation tests.

---

## 9. PaymentGatewayController — The REST Endpoint

```java
@RestController                                 // Spring: this is a REST controller
@RequestMapping("/api/payment")                 // Base URL for all endpoints in this controller
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;

    public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
        // Constructor injection — Spring injects the service implementation
    }

    @PostMapping(
            value = "/process",                         // Full URL: POST /api/payment/process
            consumes = MediaType.APPLICATION_XML_VALUE,  // Accepts: application/xml
            produces = MediaType.APPLICATION_XML_VALUE   // Returns: application/xml
    )
    public ResponseEntity<String> processPayment(@RequestBody String xmlRequest) {
        // @RequestBody: Spring reads the HTTP body and passes it as a String
        // No @Valid here — validation happens inside the service (JAXB + custom validators)

        String xmlResponse = paymentGatewayService.processPayment(xmlRequest);
        return ResponseEntity.ok(xmlResponse);
        // 200 OK with XML response body
        // If service throws any exception → GlobalExceptionHandler catches it
    }
}
```

**Why is the controller so thin?** Good design — controllers should only:
1. Receive the HTTP request
2. Delegate to the service
3. Return the HTTP response

All business logic lives in the service. This makes testing simple:
- **Service test**: tests logic without HTTP
- **Controller test**: tests HTTP routing/status codes with mocked service

---

## 10. GlobalExceptionHandler — Error Response Mapping

The handler maps each custom exception to a specific HTTP status code:

| Exception | HTTP Status | Status Code | Error Label |
|-----------|-------------|-------------|-------------|
| `InvalidCardException` | 400 Bad Request | 400 | "Invalid Card" |
| `CardExpiredException` | 400 Bad Request | 400 | "Card Expired" |
| `InvalidCVVException` | 400 Bad Request | 400 | "Invalid CVV" |
| `AmountLimitExceededException` | 400 Bad Request | 400 | "Amount Limit Exceeded" |
| `UnsupportedCurrencyException` | 400 Bad Request | 400 | "Unsupported Currency" |
| `XmlProcessingException` | 400 Bad Request | 400 | "XML Processing Error" |
| `FraudDetectedException` | 403 Forbidden | 403 | "Fraud Detected" |
| `FraudServiceUnavailableException` | 503 Service Unavailable | 503 | "Fraud Service Unavailable" |
| `PaymentDeclinedException` | 422 Unprocessable Entity | 422 | "Payment Declined" |
| `InsufficientFundsException` | 422 Unprocessable Entity | 422 | "Insufficient Funds" |
| `GatewayTimeoutException` | 504 Gateway Timeout | 504 | "Gateway Timeout" |

**Each handler returns a consistent JSON structure:**
```json
{
    "timestamp": "2026-03-16T10:30:00",
    "status": 400,
    "error": "Invalid Card",
    "message": "Card number failed Luhn validation"
}
```

**Why separate exceptions instead of one generic PaymentException?**
- Each exception → specific HTTP status code (400 vs 403 vs 422 vs 503 vs 504)
- Client knows exactly what went wrong from the error label
- Tests can assert on specific exception types

---

## 11. Custom Exception Classes

All payment exceptions follow the same pattern:

```java
public class InvalidCardException extends RuntimeException {
    public InvalidCardException(String message) {
        super(message);
    }
}
```

**Why `extends RuntimeException` (unchecked)?**
- No need for `try/catch` or `throws` declarations everywhere
- Spring's `@ExceptionHandler` catches them automatically
- Keeps service code clean — just `throw new InvalidCardException("...")` and let the handler deal with it

**Why not `extends Exception` (checked)?**
- Would force every caller to handle the exception with try/catch
- Controller would need try/catch blocks → defeats the purpose of `@ExceptionHandler`
- Spring MVC works best with unchecked exceptions for error handling

---

## 12. PaymentGatewayServiceTest — Service Unit Tests

### Setup — Mocking WebClient

```java
@ExtendWith(MockitoExtension.class)              // Enable Mockito annotations, NO Spring context
@DisplayName("PaymentGatewayService Unit Tests")
class PaymentGatewayServiceTest {

    @Mock private WebClient fraudServiceClient;            // Fake fraud WebClient
    @Mock private WebClient bankServiceClient;             // Fake bank WebClient
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;   // .post() returns this
    @Mock private WebClient.RequestBodySpec requestBodySpec;         // .uri() returns this
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;  // .bodyValue() returns this
    @Mock private WebClient.ResponseSpec responseSpec;               // .retrieve() returns this

    private PaymentGatewayServiceImpl paymentGatewayService;

    @BeforeEach
    void setUp() {
        // Create service manually — inject mocked WebClients
        paymentGatewayService = new PaymentGatewayServiceImpl(fraudServiceClient, bankServiceClient);
    }
```

**Why NOT use `@InjectMocks`?** Because the constructor uses `@Qualifier` annotations. `@InjectMocks` can't resolve qualifiers — it would inject randomly. Instead, we manually call `new PaymentGatewayServiceImpl(mock1, mock2)`.

**Why so many WebClient mocks?** WebClient uses a fluent builder pattern:
```java
webClient.post()           → returns RequestBodyUriSpec
         .uri("/api/...")  → returns RequestBodySpec
         .bodyValue(...)   → returns RequestHeadersSpec
         .retrieve()       → returns ResponseSpec
         .bodyToMono(...)  → returns Mono<T>
```
Each step returns a different interface. We must mock each one in the chain. That's why there are 4 additional mocks beyond the WebClient itself.

### Helper: Mock Bank Approval

```java
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
```

**Read it as:** "When someone calls `bankServiceClient.post().uri(anything).bodyValue(anything).retrieve().bodyToMono(BankAuthResponse.class)`, return a successful bank response."

**Why `anyString()` and `any()`?** We don't care WHAT URL or body is sent — we just want the mock to return our fake response. If we used exact values, the test would break if the URL or body format changed (brittle test).

### Helper: Build Payment XML

```java
    private String buildPaymentXml(String cardNumber, ...) {
        PaymentRequest request = PaymentRequest.builder()
                .cardNumber(cardNumber)
                // ... all fields ...
                .build();

        JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter writer = new StringWriter();
        marshaller.marshal(request, writer);
        return writer.toString();
    }
```

**Why marshal in tests?** The service expects XML input. Instead of writing XML strings manually (error-prone), we build a `PaymentRequest` object and marshal it — guaranteed valid XML structure.

### Test Pattern — Happy Path

```java
    @Test
    void processPayment_successUSD_noFraudCheck() {
        String xml = buildValidPaymentXml(500.0, "USD", false);  // Arrange: build input
        mockBankApproval();                                        // Arrange: mock external service

        String result = paymentGatewayService.processPayment(xml); // Act: call the method

        assertThat(result).contains("<status>SUCCESS</status>");   // Assert: check response
        assertThat(result).contains("<cardType>VISA</cardType>");
        verify(fraudServiceClient, never()).post();                 // Assert: fraud NOT called (amount < 1000)
    }
```

### Test Pattern — Exception Path

```java
    @Test
    void processPayment_cardNumberFailsLuhn() {
        String xml = buildPaymentXml("4111111111111112", ...);     // Arrange: bad card number

        assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))  // Act + Assert
                .isInstanceOf(InvalidCardException.class)                     // Correct exception type
                .hasMessageContaining("Luhn validation");                     // Correct message
    }
```

### Test Pattern — MockedStatic for Unreachable Catch

```java
    @Test
    void processPayment_marshalFailure() throws Exception {
        String xml = buildValidPaymentXml(500.0, "USD", false);
        mockBankApproval();

        JAXBContext realUnmarshalContext = JAXBContext.newInstance(PaymentRequest.class);
        // Save real context BEFORE mocking static — we still need unmarshal to work

        try (MockedStatic<JAXBContext> mockedJaxb = mockStatic(JAXBContext.class)) {
            // Now JAXBContext.newInstance() is mocked

            mockedJaxb.when(() -> JAXBContext.newInstance(PaymentRequest.class))
                    .thenReturn(realUnmarshalContext);
            // Step 1 unmarshal: use REAL context (let it parse XML normally)

            mockedJaxb.when(() -> JAXBContext.newInstance(PaymentResponse.class))
                    .thenThrow(new JAXBException("Mock marshal failure"));
            // Step 12 marshal: THROW exception (force the catch block)

            assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
                    .isInstanceOf(XmlProcessingException.class)
                    .hasMessageContaining("Failed to generate XML response");
        }
        // try-with-resources automatically restores JAXBContext after test
    }
```

**This is the most advanced testing technique in the file.** It uses `MockedStatic` to:
1. Let the FIRST call to `JAXBContext.newInstance(PaymentRequest.class)` work normally
2. Make the SECOND call to `JAXBContext.newInstance(PaymentResponse.class)` throw an exception
3. This covers the catch block in Step 12 that would never execute in normal code

---

## 13. PaymentGatewayControllerTest — Controller Tests

```java
@WebMvcTest(PaymentGatewayController.class)       // Load ONLY the controller + exception handler
@DisplayName("PaymentGatewayController WebMvcTest")
class PaymentGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;                        // Simulates HTTP requests without a real server

    @MockitoBean
    private PaymentGatewayService paymentGatewayService;  // Mock the service
```

**Why `@WebMvcTest` + `@MockitoBean`?**
- `@WebMvcTest` loads ONLY: the controller, exception handler, and Spring MVC config
- `@MockitoBean` replaces the real service with a mock
- No database, no WebClient, no Kafka — just HTTP layer

### Test Pattern — Success

```java
    @Test
    void processPayment_success() throws Exception {
        when(paymentGatewayService.processPayment(anyString())).thenReturn(responseXml);
        // Service returns XML (we don't care about logic, just that controller passes it through)

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)    // Tell Spring "I'm sending XML"
                        .content(requestXml))                       // The XML body
                .andExpect(status().isOk())                         // Expect 200
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(responseXml));           // Expect exact XML back
    }
```

### Test Pattern — Exception → HTTP Status

```java
    @Test
    void processPayment_invalidCard_returns400() throws Exception {
        when(paymentGatewayService.processPayment(anyString()))
                .thenThrow(new InvalidCardException("Card number failed Luhn validation"));
        // Service throws exception

        mockMvc.perform(post(PAYMENT_URL)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(requestXml))
                .andExpect(status().isBadRequest())                                     // 400
                .andExpect(jsonPath("$.error").value("Invalid Card"))                   // Error label
                .andExpect(jsonPath("$.message").value("Card number failed Luhn validation")); // Message
    }
```

**What this tests (that service test doesn't):**
- The HTTP status code is correct (400, not 500)
- The `GlobalExceptionHandler` catches the exception properly
- The JSON response structure is correct (`$.error`, `$.message`)
- The content type is correct

**Each exception gets its own controller test** to verify the exception handler mapping.

---

## Complete Branch Count

| Source Code Section | Branches | Tests |
|--------------------|----------|-------|
| Step 1: XML unmarshal try/catch | 2 | 3 |
| Step 2a: validateCardNumber | 4 | 5 |
| Step 2a: isValidLuhn (inner loops) | 3 | covered by card tests |
| Step 2b: validateExpiryDate | 4 | 4 |
| Step 2c: validateCVV | 4 | 4 |
| Step 2d: validateAmount | 4 | 4 |
| Step 2e: validateCurrency | 3 | 3 |
| Step 3: determineCardType | 5 | 6 |
| Step 4: Fraud check | 7 | 7 |
| Step 5: calculateProcessingFee switch | 5 | 5 |
| Step 6: Currency conversion if | 2 | 8 (each currency) |
| Step 7: Loyalty discount | 4 | 4 |
| Step 10: Bank authorization | 8 | 8 |
| Step 12: XML marshal try/catch | 2 | 1 (MockedStatic) |
| maskCardNumber | 2 | 2 |
| **TOTAL** | **~59 branches** | **62 tests** |
