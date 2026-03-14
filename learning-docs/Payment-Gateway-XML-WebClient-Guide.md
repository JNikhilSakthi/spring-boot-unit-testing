# Payment Gateway — XML Processing, WebClient & Exception Handling Guide

---

## Table of Contents

1. [What Did We Build?](#1-what-did-we-build)
2. [Why This Feature? — Real-World Context](#2-why-this-feature--real-world-context)
3. [Complete Request → Response Flow](#3-complete-request--response-flow)
4. [Block-by-Block Detailed Explanation](#4-block-by-block-detailed-explanation)
5. [JAXB — XML Marshalling & Unmarshalling](#5-jaxb--xml-marshalling--unmarshalling)
6. [WebClient — Calling External Services](#6-webclient--calling-external-services)
7. [Business Logic Breakdown (12 Steps)](#7-business-logic-breakdown-12-steps)
8. [Exception Handling Strategy](#8-exception-handling-strategy)
9. [All Classes & Their Purpose](#9-all-classes--their-purpose)
10. [Testing Strategy — How We Tested Everything](#10-testing-strategy--how-we-tested-everything)
11. [Test Categories & What They Cover](#11-test-categories--what-they-cover)
12. [Key Patterns Used in Tests](#12-key-patterns-used-in-tests)
13. [Common Interview Questions This Covers](#13-common-interview-questions-this-covers)

---

## 1. What Did We Build?

A **Payment Gateway API** that:
- Receives a **payment request as XML string**
- **Unmarshals** (converts) the XML string → Java object using JAXB
- **Validates** card details (Luhn algorithm, expiry, CVV, amount, currency)
- Calls **Fraud Detection Service** via WebClient (if amount > 1000)
- Calculates **processing fees**, **currency conversion**, **loyalty discounts**, and **tax**
- Calls **Bank Authorization Service** via WebClient
- **Marshals** (converts) the response Java object → XML string
- Returns the **XML string as the API response**

```
┌──────────────┐      ┌──────────────────────┐      ┌──────────────┐
│  Client      │      │  PaymentGateway      │      │  External    │
│  (XML POST)  │─────▶│  Service             │─────▶│  Services    │
│              │      │                      │      │              │
│              │◀─────│  Unmarshal → Logic   │◀─────│  Fraud API   │
│  (XML Response)     │  → Marshal           │      │  Bank API    │
└──────────────┘      └──────────────────────┘      └──────────────┘
```

---

## 2. Why This Feature? — Real-World Context

### Why XML?

Many enterprise systems (banking, insurance, government) still use XML — not JSON:
- **SOAP-based services** communicate in XML
- **Legacy banking APIs** send/receive XML
- **Payment processors** like ISO 8583 often use XML wrappers

### Why WebClient?

WebClient is the **modern replacement for RestTemplate** in Spring:
```
RestTemplate (old)  → Synchronous, blocking, deprecated for new projects
WebClient (new)     → Non-blocking, reactive, supports sync & async
```

### Why String input/output instead of direct object?

In real payment systems, the raw XML often arrives as a string from:
- Message queues (Kafka, RabbitMQ)
- File uploads (batch payment processing)
- Legacy SOAP endpoints
- Third-party APIs that return raw XML

We deliberately unmarshal/marshal **manually** to demonstrate JAXB usage.

---

## 3. Complete Request → Response Flow

### Request XML (what the client sends):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<PaymentRequest>
    <cardNumber>4111111111111111</cardNumber>
    <cardHolderName>John Doe</cardHolderName>
    <expiryMonth>12</expiryMonth>
    <expiryYear>2028</expiryYear>
    <cvv>123</cvv>
    <amount>1500.00</amount>
    <currency>USD</currency>
    <merchantId>MERCH001</merchantId>
    <loyaltyMember>true</loyaltyMember>
    <description>Purchase from E-Commerce Store</description>
</PaymentRequest>
```

### Response XML (what the API returns):

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<PaymentResponse>
    <transactionId>TXN-123456</transactionId>
    <status>SUCCESS</status>
    <message>Payment processed successfully</message>
    <originalAmount>1500.0</originalAmount>
    <currency>USD</currency>
    <exchangeRate>1.0</exchangeRate>
    <convertedAmount>1500.0</convertedAmount>
    <processingFee>30.0</processingFee>
    <discount>15.0</discount>
    <tax>29.7</tax>
    <finalAmount>1544.7</finalAmount>
    <cardType>VISA</cardType>
    <maskedCardNumber>****1111</maskedCardNumber>
    <fraudStatus>CLEAR</fraudStatus>
    <riskScore>20</riskScore>
    <bankReferenceId>BNK-789</bankReferenceId>
    <processedAt>2026-03-14T16:30:00</processedAt>
</PaymentResponse>
```

### Complete 12-Step Flow Diagram:

```
Client sends XML String
        │
        ▼
┌─ STEP 1: UNMARSHAL ─────────────────────────┐
│  XML String → JAXBContext → Unmarshaller     │
│  → PaymentRequest Java Object                │
│  ✗ Failure → XmlProcessingException          │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 2: VALIDATE INPUTS ───────────────────┐
│  validateCardNumber()  → Luhn algorithm      │
│  validateExpiryDate()  → Not expired         │
│  validateCVV()         → 3 or 4 digits       │
│  validateAmount()      → > 0, < 100000       │
│  validateCurrency()    → USD/EUR/GBP etc.    │
│  ✗ Any failure → specific exception thrown   │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 3: DETERMINE CARD TYPE ───────────────┐
│  Starts with 4 → VISA                       │
│  Starts with 5 → MASTERCARD                 │
│  Starts with 34/37 → AMEX                   │
│  Starts with 6 → DISCOVER                   │
│  Other → UNKNOWN                             │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 4: FRAUD CHECK (if amount > 1000) ────┐
│  WebClient POST → Fraud Detection Service    │
│  Risk Score > 80  → FraudDetectedException   │
│  Risk Score > 50  → MANUAL_REVIEW (continue) │
│  Risk Score ≤ 50  → CLEAR (continue)         │
│  Service down → FraudServiceUnavailableEx    │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 5: PROCESSING FEE ────────────────────┐
│  VISA: 2.0%  │  MASTERCARD: 2.2%            │
│  AMEX: 2.5%  │  DISCOVER: 1.8%              │
│  UNKNOWN: 3.0%                               │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 6: CURRENCY CONVERSION ───────────────┐
│  EUR→USD: ×1.08  │  GBP→USD: ×1.27          │
│  INR→USD: ×0.012 │  JPY→USD: ×0.0067        │
│  AUD→USD: ×0.65  │  CAD→USD: ×0.74          │
│  USD: no conversion (rate = 1.0)             │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 7: LOYALTY DISCOUNT ──────────────────┐
│  Amount > 5000  → 5% discount                │
│  Amount > 2000  → 3% discount                │
│  Amount ≤ 2000  → 1% discount                │
│  Not loyalty member → 0% (no discount)       │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 8-9: TAX & FINAL AMOUNT ──────────────┐
│  Tax = 2% of (convertedAmount - discount)    │
│  Final = converted + fee - discount + tax    │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 10: BANK AUTHORIZATION ───────────────┐
│  WebClient POST → Bank Service               │
│  APPROVED → get transactionId, referenceId   │
│  DECLINED → PaymentDeclinedException         │
│  DECLINED (INSUFFICIENT_FUNDS) → InsufficientFundsEx │
│  Timeout → GatewayTimeoutException           │
│  Null response → GatewayTimeoutException     │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 11: BUILD RESPONSE ───────────────────┐
│  PaymentResponse.builder()                   │
│    .transactionId(), .status("SUCCESS")      │
│    .finalAmount(), .cardType() etc.          │
│    .build()                                  │
└──────────────────────────────────────────────┘
        │
        ▼
┌─ STEP 12: MARSHAL ──────────────────────────┐
│  PaymentResponse → JAXBContext → Marshaller  │
│  → XML String                                │
│  ✗ Failure → XmlProcessingException          │
└──────────────────────────────────────────────┘
        │
        ▼
  Return XML String to Client
```

---

## 4. Block-by-Block Detailed Explanation

This is the **high-level flow diagram** of the entire payment processing pipeline. Below the diagram, every single block is explained in detail — what it does, why we need it, the actual code, and what happens when things go wrong.

```
XML Request (Payment)
  │
  ├── JAXB Unmarshal
  │
  ├── Validate card number (Luhn algorithm)
  ├── Validate expiry date (not expired, not too far future)
  ├── Validate CVV (3 or 4 digits)
  ├── Validate amount (> 0, max limit, decimal precision)
  ├── Validate currency (supported currencies list)
  │
  ├── if amount > 1000 → WebClient call #1: Fraud Detection API
  │     ├── if risk score > 80 → FraudDetectedException
  │     ├── if risk score > 50 → flag for manual review
  │     └── if service down → FraudServiceUnavailableException
  │
  ├── Based on card type (VISA/MASTERCARD/AMEX):
  │     ├── calculate processing fee (different % per type)
  │     ├── apply currency conversion if needed
  │     └── apply discount if loyalty customer
  │
  ├── WebClient call #2: Bank Authorization API
  │     ├── if approved → generate transaction ID
  │     ├── if declined → PaymentDeclinedException
  │     ├── if timeout → GatewayTimeoutException
  │     └── if insufficient funds → InsufficientFundsException
  │
  ├── Calculate final amount (amount + fee - discount + tax)
  │
  ├── JAXB Marshal response
  │
  └── Return XML Response (transactionId, status, breakdown)
```

Now let's understand **each block** in detail:

---

### BLOCK 1: JAXB Unmarshal (XML String → Java Object)

```
XML Request (Payment)
  │
  ├── JAXB Unmarshal
```

**What does this block do?**

The client sends the payment data as a **raw XML string** in the request body. But we can't work with raw XML in Java — we need a **Java object** with proper fields (cardNumber, amount, etc.) so we can access data with getters like `request.getCardNumber()`.

**Unmarshal** = converting XML text into a Java object.

**How it works step by step:**

```java
// Step 1: Create a JAXB context — tells JAXB "I want to work with PaymentRequest class"
JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);

// Step 2: Create an unmarshaller — this is the object that does the XML → Java conversion
Unmarshaller unmarshaller = context.createUnmarshaller();

// Step 3: Wrap the XML string in a StringReader (JAXB reads from Reader, not String)
StringReader reader = new StringReader(xmlRequest);

// Step 4: Unmarshal — reads the XML and creates a PaymentRequest object
PaymentRequest paymentRequest = (PaymentRequest) unmarshaller.unmarshal(reader);

// Now we can do:
// paymentRequest.getCardNumber()  → "4111111111111111"
// paymentRequest.getAmount()      → 1500.0
// paymentRequest.getCurrency()    → "USD"
```

**What can go wrong?**

| Problem | Example | Exception Thrown |
|---------|---------|-----------------|
| XML is not valid XML at all | `"this is not xml <<<>>>"` | `XmlProcessingException` |
| XML tags don't match `PaymentRequest` | `<SomeOtherTag>data</SomeOtherTag>` | `XmlProcessingException` |
| Empty string | `""` | `XmlProcessingException` |

**Why try-catch here?**

```java
try {
    // unmarshal code...
} catch (JAXBException e) {
    // JAXBException is a checked exception — Java forces us to handle it
    // We wrap it in our custom XmlProcessingException (unchecked)
    // so GlobalExceptionHandler can catch it and return 400 Bad Request
    throw new XmlProcessingException("Failed to parse XML request: " + e.getMessage());
}
```

**Why wrap JAXBException in XmlProcessingException?**

- `JAXBException` is a **checked exception** (must be declared in throws or caught)
- Our custom `XmlProcessingException` is a **RuntimeException** (unchecked)
- Spring's `@ExceptionHandler` works better with unchecked exceptions
- We get a **clean error message** for the client instead of a raw JAXB stack trace

---

### BLOCK 2: Validate Card Number (Luhn Algorithm)

```
  ├── Validate card number (Luhn algorithm)
```

**What does this block do?**

Before we process any payment, we check if the credit card number is **actually valid**. This is the first line of defense — if the card number is garbage, there's no point calling fraud services or the bank.

**Three checks happen in order:**

**Check 1: Is the card number present?**
```java
if (cardNumber == null || cardNumber.isBlank()) {
    throw new InvalidCardException("Card number is required");
}
```
- Why? A null or empty string is obviously not a valid card number.

**Check 2: Is it the right format (13-19 digits)?**
```java
String cleaned = cardNumber.replaceAll("\\s+", "");  // Remove spaces: "4111 1111 1111 1111" → "4111111111111111"

if (!cleaned.matches("\\d{13,19}")) {
    throw new InvalidCardException("Card number must be 13-19 digits");
}
```
- Why 13-19? VISA cards have 13 or 16 digits, Mastercard has 16, AMEX has 15. The range 13-19 covers all card types.
- `\\d{13,19}` = regex meaning "exactly 13 to 19 digits, nothing else"
- `replaceAll("\\s+", "")` = removes spaces (some people type card numbers with spaces)

**Check 3: Does it pass the Luhn algorithm?**
```java
if (!isValidLuhn(cleaned)) {
    throw new InvalidCardException("Card number failed Luhn validation");
}
```

**What is the Luhn algorithm and why do we need it?**

The Luhn algorithm is a simple checksum formula that detects **typos** in card numbers. Every real credit card number passes this check. If someone types a wrong digit, Luhn will catch it.

```
How Luhn works (example: 4111111111111111):

Original:      4  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
               ↑     ↑     ↑     ↑     ↑     ↑     ↑     ↑
Step 1: Starting from the RIGHT, double every SECOND digit:
               8  1  2  1  2  1  2  1  2  1  2  1  2  1  2  1

Step 2: If any doubled digit > 9, subtract 9:
               8  1  2  1  2  1  2  1  2  1  2  1  2  1  2  1
               (all ≤ 9 here, so no changes)

Step 3: Add all digits together:
               8+1+2+1+2+1+2+1+2+1+2+1+2+1+2+1 = 30

Step 4: If total % 10 == 0 → VALID
               30 % 10 = 0 → VALID ✓

If someone typed 4111111111111112 (last digit wrong):
               Sum = 31, 31 % 10 = 1 → INVALID ✗
```

**The actual code:**
```java
private boolean isValidLuhn(String cardNumber) {
    int sum = 0;
    boolean alternate = false;

    // Loop from RIGHT to LEFT
    for (int i = cardNumber.length() - 1; i >= 0; i--) {
        int digit = Character.getNumericValue(cardNumber.charAt(i));

        if (alternate) {
            digit *= 2;           // Double every second digit
            if (digit > 9) {
                digit -= 9;       // Subtract 9 if > 9
            }
        }

        sum += digit;
        alternate = !alternate;   // Toggle: false → true → false → true...
    }

    return sum % 10 == 0;        // Valid if divisible by 10
}
```

---

### BLOCK 3: Validate Expiry Date

```
  ├── Validate expiry date (not expired, not too far future)
```

**What does this block do?**

Checks if the card's expiry date is **valid** — not in the past (expired), not invalid months, and not ridiculously far in the future.

**Three checks happen:**

```java
// Check 1: Is the month valid? (must be 1-12)
if (month < 1 || month > 12) {
    throw new CardExpiredException("Invalid expiry month: " + month);
}
// Why? Month 0 or month 13 doesn't exist. This catches bad input.

// Check 2: Is the card expired?
YearMonth expiry = YearMonth.of(year, month);  // e.g., YearMonth.of(2028, 12) = December 2028
YearMonth now = YearMonth.now();               // e.g., March 2026

if (expiry.isBefore(now)) {
    throw new CardExpiredException("Card has expired: 01/2020");
}
// Why? If expiry is January 2020 and today is March 2026, the card expired 6 years ago.

// Check 3: Is the expiry too far in the future? (max 10 years)
YearMonth maxFuture = now.plusYears(10);       // March 2036
if (expiry.isAfter(maxFuture)) {
    throw new CardExpiredException("Expiry date is too far in the future: 12/2040");
}
// Why? No real card expires 15+ years from now. This catches fake/test card numbers.
```

**Why use `YearMonth` instead of `LocalDate`?**

Credit cards expire at the **end of a month** (not a specific day). `YearMonth` represents exactly that — a month + year combination without a day.

---

### BLOCK 4: Validate CVV

```
  ├── Validate CVV (3 or 4 digits)
```

**What does this block do?**

CVV (Card Verification Value) is the 3 or 4 digit security code on the back (or front for AMEX) of a credit card.

```java
// Check 1: Is CVV present?
if (cvv == null || cvv.isBlank()) {
    throw new InvalidCVVException("CVV is required");
}

// Check 2: Is it the right length based on card type?
boolean isAmex = cleanedCard.startsWith("34") || cleanedCard.startsWith("37");

if (isAmex) {
    if (!cvv.matches("\\d{4}")) {  // AMEX uses 4-digit CVV
        throw new InvalidCVVException("AMEX cards require a 4-digit CVV");
    }
} else {
    if (!cvv.matches("\\d{3}")) {  // All other cards use 3-digit CVV
        throw new InvalidCVVException("CVV must be 3 digits");
    }
}
```

**Why different lengths for AMEX?**

| Card Type | CVV Name | Digits | Location |
|-----------|----------|--------|----------|
| VISA / Mastercard / Discover | CVV2 / CVC2 | 3 digits | Back of card |
| AMEX | CID (Card ID) | 4 digits | Front of card |

This is a real-world rule — AMEX genuinely uses 4-digit security codes.

---

### BLOCK 5: Validate Amount

```
  ├── Validate amount (> 0, max limit, decimal precision)
```

**What does this block do?**

Makes sure the payment amount is sensible — not negative, not zero, not too high, and not something weird like $100.123.

```java
// Check 1: Must be positive
if (amount <= 0) {
    throw new AmountLimitExceededException("Payment amount must be greater than zero");
}
// Why? You can't pay negative money or zero money.

// Check 2: Must not exceed maximum ($100,000)
if (amount > 100000.0) {
    throw new AmountLimitExceededException("Payment amount exceeds maximum limit of 100000.0");
}
// Why? Real payment gateways have transaction limits to prevent fraud.

// Check 3: Maximum 2 decimal places
BigDecimal bd = BigDecimal.valueOf(amount);
if (bd.scale() > 2) {
    throw new AmountLimitExceededException("Payment amount cannot have more than 2 decimal places");
}
// Why? Money only goes to cents (2 decimals). $100.123 doesn't make sense.
// BigDecimal.valueOf(100.12).scale() = 2  ← OK
// BigDecimal.valueOf(100.123).scale() = 3 ← Bad
```

---

### BLOCK 6: Validate Currency

```
  ├── Validate currency (supported currencies list)
```

**What does this block do?**

Checks if we support the currency the customer wants to pay with.

```java
private static final List<String> SUPPORTED_CURRENCIES = List.of(
    "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD"
);

// Check 1: Is currency present?
if (currency == null || currency.isBlank()) {
    throw new UnsupportedCurrencyException("Currency is required");
}

// Check 2: Is it in our supported list?
if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
    throw new UnsupportedCurrencyException(
        "Currency 'BTC' is not supported. Supported: [USD, EUR, GBP, INR, JPY, AUD, CAD]");
}
```

**Why only 7 currencies?**

In a real system, you'd support more. We keep it simple for learning. The pattern is the same — validate against a list and reject unknowns.

---

### BLOCK 7: Fraud Detection (WebClient Call #1)

```
  ├── if amount > 1000 → WebClient call #1: Fraud Detection API
  │     ├── if risk score > 80 → FraudDetectedException
  │     ├── if risk score > 50 → flag for manual review
  │     └── if service down → FraudServiceUnavailableException
```

**What does this block do?**

For large transactions (> $1000), we call an **external fraud detection service** via WebClient. This service analyzes the transaction and returns a **risk score** (0-100). Based on the score, we decide whether to proceed, flag for review, or block the transaction.

**Why only for amount > 1000?**

Small transactions have low fraud risk. Calling an external service for every $5 coffee purchase would be slow and expensive. Real payment gateways use similar thresholds.

**The WebClient call:**

```java
if (paymentRequest.getAmount() > FRAUD_CHECK_THRESHOLD) {  // 1000.0
    try {
        FraudCheckResponse fraudResponse = fraudServiceClient
            .post()                              // HTTP POST method
            .uri("/api/fraud/check")             // Endpoint on fraud service
            .bodyValue(Map.of(                   // What we send to the fraud service
                "cardNumber", maskCardNumber(paymentRequest.getCardNumber()),  // ****1111 (masked!)
                "amount", paymentRequest.getAmount(),
                "currency", paymentRequest.getCurrency()
            ))
            .retrieve()                          // Execute the HTTP call
            .bodyToMono(FraudCheckResponse.class) // Convert response JSON → Java object
            .block();                            // Wait for result (synchronous)
```

**Why do we mask the card number before sending to the fraud service?**

Security! We never send the **full card number** to external services. We only send `****1111` (last 4 digits). The fraud service doesn't need the full number — it uses the amount, currency, and pattern to assess risk.

**Processing the fraud response:**

```java
        if (fraudResponse != null) {
            riskScore = fraudResponse.getRiskScore();

            if (riskScore > 80) {
                // HIGH RISK — block the transaction immediately
                throw new FraudDetectedException(
                    "Transaction flagged as fraudulent. Risk score: " + riskScore);
            } else if (riskScore > 50) {
                // MEDIUM RISK — allow but flag for human review later
                fraudStatus = "MANUAL_REVIEW";
            } else {
                // LOW RISK — all clear, proceed normally
                fraudStatus = "CLEAR";
            }
        }
```

**Risk Score Decision Table:**

| Risk Score | Action | What Happens |
|-----------|--------|--------------|
| 0-50 | CLEAR | Transaction proceeds normally |
| 51-80 | MANUAL_REVIEW | Transaction proceeds, but flagged for human review later |
| 81-100 | BLOCKED | Transaction is immediately rejected |

**The try-catch structure — why is it complex?**

```java
    } catch (FraudDetectedException e) {
        throw e;  // Re-throw — this is OUR exception, not a service error
    } catch (WebClientResponseException e) {
        // The fraud service responded but with an HTTP error (503, 500, etc.)
        throw new FraudServiceUnavailableException("Service returned error: " + e.getStatusCode());
    } catch (Exception e) {
        // Connection refused, DNS failure, timeout, etc.
        if (e instanceof FraudDetectedException) {
            throw (FraudDetectedException) e;  // Safety re-throw
        }
        throw new FraudServiceUnavailableException("Service unavailable: " + e.getMessage());
    }
}
```

**Why do we need to re-throw FraudDetectedException?**

Because it's thrown INSIDE the try block (when riskScore > 80). Without re-throwing, the generic `catch (Exception e)` would catch it and wrongly turn it into a "Service Unavailable" error instead of "Fraud Detected".

```
Without re-throw:
  Risk score 90 → FraudDetectedException → caught by catch(Exception) → "Service Unavailable" ✗ WRONG!

With re-throw:
  Risk score 90 → FraudDetectedException → caught by catch(FraudDetectedException) → re-thrown → "Fraud Detected" ✓ CORRECT!
```

---

### BLOCK 8: Processing Fee, Currency Conversion & Loyalty Discount

```
  ├── Based on card type (VISA/MASTERCARD/AMEX):
  │     ├── calculate processing fee (different % per type)
  │     ├── apply currency conversion if needed
  │     └── apply discount if loyalty customer
```

This block actually has 3 separate sub-blocks. Let's go through each:

#### Sub-Block 8a: Card Type Detection & Processing Fee

**What does this do?**

Different card networks charge different fees to merchants. We calculate the fee based on what card the customer is using.

```java
// Determine card type by looking at the first digit(s)
private String determineCardType(String cardNumber) {
    String cleaned = cardNumber.replaceAll("\\s+", "");

    if (cleaned.startsWith("4"))                               return "VISA";
    else if (cleaned.startsWith("5"))                          return "MASTERCARD";
    else if (cleaned.startsWith("34") || cleaned.startsWith("37")) return "AMEX";
    else if (cleaned.startsWith("6"))                          return "DISCOVER";
    else                                                        return "UNKNOWN";
}
```

**Why do card numbers start with specific digits?**

This is a real-world standard called **BIN (Bank Identification Number)**:
- `4xxx` = VISA
- `5xxx` = Mastercard
- `34xx` or `37xx` = American Express
- `6xxx` = Discover

Every real credit card follows this rule.

```java
// Calculate fee as a percentage of the transaction amount
private double calculateProcessingFee(double amount, String cardType) {
    double feePercentage = switch (cardType) {
        case "VISA"       -> 0.020;  // 2.0%
        case "MASTERCARD" -> 0.022;  // 2.2%
        case "AMEX"       -> 0.025;  // 2.5%  ← AMEX charges more (real-world too!)
        case "DISCOVER"   -> 0.018;  // 1.8%
        default           -> 0.030;  // 3.0% for unknown cards (higher risk)
    };
    return roundToTwoDecimals(amount * feePercentage);
}
// Example: $1000 with VISA = $1000 × 0.020 = $20.00 fee
```

#### Sub-Block 8b: Currency Conversion

**What does this do?**

If the customer pays in a non-USD currency, we convert the amount to USD using exchange rates.

```java
double convertedAmount = paymentRequest.getAmount();
double exchangeRate = 1.0;

if (!"USD".equalsIgnoreCase(paymentRequest.getCurrency())) {
    exchangeRate = getExchangeRate(paymentRequest.getCurrency());
    convertedAmount = roundToTwoDecimals(paymentRequest.getAmount() * exchangeRate);
}
```

**Exchange rates used:**

| From | To USD | Example: 100 units |
|------|--------|---------------------|
| USD | ×1.0 | $100.00 |
| EUR | ×1.08 | $108.00 |
| GBP | ×1.27 | $127.00 |
| INR | ×0.012 | $1.20 |
| JPY | ×0.0067 | $0.67 |
| AUD | ×0.65 | $65.00 |
| CAD | ×0.74 | $74.00 |

**Why convert to USD?**

The bank authorization service works in USD. All internal calculations are done in USD. The original currency and exchange rate are stored in the response so the customer can see the conversion.

#### Sub-Block 8c: Loyalty Discount

**What does this do?**

If the customer is a loyalty member, they get a discount based on their transaction amount.

```java
double discount = 0.0;

if (paymentRequest.isLoyaltyMember()) {
    if (convertedAmount > 5000) {
        discount = roundToTwoDecimals(convertedAmount * 0.05);  // 5% discount
    } else if (convertedAmount > 2000) {
        discount = roundToTwoDecimals(convertedAmount * 0.03);  // 3% discount
    } else {
        discount = roundToTwoDecimals(convertedAmount * 0.01);  // 1% discount
    }
}
// Not a loyalty member? discount stays 0.0
```

**Discount tiers:**

| Converted Amount | Discount | On $6000 | On $3000 | On $500 |
|-----------------|----------|----------|----------|---------|
| > $5000 | 5% | $300 | — | — |
| > $2000 | 3% | — | $90 | — |
| ≤ $2000 | 1% | — | — | $5 |
| Not loyalty | 0% | $0 | $0 | $0 |

**Why `if-else` and not just `if-if-if`?**

Because the tiers are **mutually exclusive**. A $6000 transaction should get 5%, not 5% + 3% + 1%. The `else if` ensures only **one** tier applies.

---

### BLOCK 9: Bank Authorization (WebClient Call #2)

```
  ├── WebClient call #2: Bank Authorization API
  │     ├── if approved → generate transaction ID
  │     ├── if declined → PaymentDeclinedException
  │     ├── if timeout → GatewayTimeoutException
  │     └── if insufficient funds → InsufficientFundsException
```

**What does this block do?**

After all validations and calculations, we ask the **bank** to actually authorize the payment. This is the most critical step — the bank checks if the customer's account has enough money and if the transaction is allowed.

**The WebClient call:**

```java
BankAuthResponse bankResponse = bankServiceClient
    .post()
    .uri("/api/bank/authorize")
    .bodyValue(Map.of(
        "cardNumber", paymentRequest.getCardNumber(),  // Full card number (bank needs it)
        "amount", finalAmount,                         // Final amount after all calculations
        "currency", "USD",                             // Always USD (we already converted)
        "merchantId", paymentRequest.getMerchantId()   // Who is the merchant
    ))
    .retrieve()
    .bodyToMono(BankAuthResponse.class)
    .block();
```

**Notice:** We send the **full card number** to the bank (not masked). The bank needs it to process the payment. This is different from the fraud service where we sent masked numbers.

**Processing the bank response — multiple if-else branches:**

```java
// Branch 1: No response at all
if (bankResponse == null) {
    throw new GatewayTimeoutException("No response received from bank authorization service");
}

// Branch 2: Bank says DECLINED
if ("DECLINED".equalsIgnoreCase(bankResponse.getStatus())) {
    // Sub-branch: WHY was it declined?
    if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(bankResponse.getDeclineReason())) {
        throw new InsufficientFundsException("Insufficient funds for transaction amount: " + finalAmount);
    }
    // Any other decline reason (CARD_BLOCKED, SUSPECTED_FRAUD, etc.)
    throw new PaymentDeclinedException("Payment declined by bank: " + bankResponse.getDeclineReason());
}

// Branch 3: Bank says something unexpected (not APPROVED, not DECLINED)
if (!"APPROVED".equalsIgnoreCase(bankResponse.getStatus())) {
    throw new PaymentDeclinedException("Unexpected bank response status: " + bankResponse.getStatus());
}

// Branch 4: APPROVED — success!
transactionId = bankResponse.getTransactionId();
bankReferenceId = bankResponse.getReferenceId();
```

**The try-catch structure:**

```java
} catch (PaymentDeclinedException | InsufficientFundsException | GatewayTimeoutException e) {
    throw e;  // Re-throw our business exceptions as-is
} catch (WebClientResponseException.GatewayTimeout e) {
    // Specific: HTTP 504 from the bank
    throw new GatewayTimeoutException("Bank authorization service timed out");
} catch (WebClientResponseException e) {
    // Any other HTTP error (500, 503, etc.)
    throw new PaymentDeclinedException("Bank service error: " + e.getStatusCode());
} catch (Exception e) {
    // Connection refused, DNS failure, etc.
    if (e instanceof PaymentDeclinedException || e instanceof InsufficientFundsException
            || e instanceof GatewayTimeoutException) {
        throw (RuntimeException) e;  // Safety re-throw
    }
    throw new GatewayTimeoutException("Bank authorization failed: " + e.getMessage());
}
```

**Why `catch (WebClientResponseException.GatewayTimeout e)` separately?**

`WebClientResponseException` has inner classes for specific HTTP status codes:
- `WebClientResponseException.GatewayTimeout` = HTTP 504
- `WebClientResponseException.ServiceUnavailable` = HTTP 503
- `WebClientResponseException.InternalServerError` = HTTP 500

We catch 504 separately because a **timeout** is different from a **server error** — timeout means "try again later", server error means "something is broken".

---

### BLOCK 10: Calculate Final Amount

```
  ├── Calculate final amount (amount + fee - discount + tax)
```

**What does this block do?**

Combines all the calculated values into the final amount the customer will be charged.

```java
// Tax = 2% of (converted amount minus discount)
double tax = roundToTwoDecimals((convertedAmount - discount) * 0.02);

// Final amount = everything added together
double finalAmount = roundToTwoDecimals(convertedAmount + processingFee - discount + tax);
```

**Why subtract discount before calculating tax?**

In most real-world systems, **tax is calculated on the discounted amount**, not the original. If you buy something for $100 and get a $10 discount, you pay tax on $90, not $100.

**Complete example with a $1000 VISA USD payment by a loyalty member:**

```
convertedAmount  = 1000.00  (USD, no conversion needed)
processingFee    = 20.00    (VISA 2.0% × 1000)
discount         = 10.00    (loyalty 1% × 1000, since ≤ 2000)
tax              = 19.80    (2% × (1000 - 10) = 2% × 990)
─────────────────────────────────────────
finalAmount      = 1000 + 20 - 10 + 19.80 = 1029.80
```

**Why `roundToTwoDecimals()`?**

```java
private double roundToTwoDecimals(double value) {
    return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
}
```

Floating-point arithmetic can produce results like `19.800000000000004`. We round to 2 decimal places because money doesn't have fractions of cents. `HALF_UP` means 19.805 rounds to 19.81 (standard rounding).

---

### BLOCK 11: JAXB Marshal (Java Object → XML String)

```
  ├── JAXB Marshal response
```

**What does this block do?**

The opposite of Block 1. We've built a `PaymentResponse` Java object with all the results. Now we need to convert it back to an **XML string** to send to the client.

**Marshal** = converting a Java object into XML text.

```java
// Step 1: Build the response object with all calculated values
PaymentResponse response = PaymentResponse.builder()
    .transactionId(transactionId)          // From bank: "TXN-123456"
    .status("SUCCESS")                     // We made it this far = success
    .message("Payment processed successfully")
    .originalAmount(paymentRequest.getAmount())   // What customer sent: 1000.0
    .currency(paymentRequest.getCurrency())       // Original currency: "USD"
    .exchangeRate(exchangeRate)                   // Conversion rate used: 1.0
    .convertedAmount(convertedAmount)             // After conversion: 1000.0
    .processingFee(processingFee)                 // Card fee: 20.0
    .discount(discount)                           // Loyalty discount: 10.0
    .tax(tax)                                     // Processing tax: 19.80
    .finalAmount(finalAmount)                     // Total charged: 1029.80
    .cardType(cardType)                           // "VISA"
    .maskedCardNumber(maskCardNumber(...))         // "****1111"
    .fraudStatus(fraudStatus)                     // "CLEAR" or "MANUAL_REVIEW"
    .riskScore(riskScore)                         // 0-100
    .bankReferenceId(bankReferenceId)             // From bank: "BNK-789"
    .processedAt(LocalDateTime.now().toString())  // When we processed it
    .build();

// Step 2: Marshal to XML string
JAXBContext context = JAXBContext.newInstance(PaymentResponse.class);
Marshaller marshaller = context.createMarshaller();
marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);  // Pretty print with indentation
StringWriter writer = new StringWriter();
marshaller.marshal(response, writer);
return writer.toString();
```

**What does `JAXB_FORMATTED_OUTPUT = true` do?**

```xml
<!-- Without (one long line): -->
<?xml version="1.0"?><PaymentResponse><transactionId>TXN-123456</transactionId><status>SUCCESS</status>...</PaymentResponse>

<!-- With (pretty printed): -->
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<PaymentResponse>
    <transactionId>TXN-123456</transactionId>
    <status>SUCCESS</status>
    <message>Payment processed successfully</message>
    ...
</PaymentResponse>
```

---

### BLOCK 12: Return XML Response

```
  └── Return XML Response (transactionId, status, breakdown)
```

**What does this block do?**

The controller receives the XML string from the service and wraps it in an HTTP response with status 200 OK and content type `application/xml`.

```java
@PostMapping(
    value = "/process",
    consumes = MediaType.APPLICATION_XML_VALUE,   // Accepts XML input
    produces = MediaType.APPLICATION_XML_VALUE     // Returns XML output
)
public ResponseEntity<String> processPayment(@RequestBody String xmlRequest) {
    String xmlResponse = paymentGatewayService.processPayment(xmlRequest);
    return ResponseEntity.ok(xmlResponse);  // 200 OK with XML body
}
```

**What does `@RequestBody String xmlRequest` do?**

Spring takes the **entire HTTP request body** (the raw XML text) and passes it as a Java `String`. We don't use `@Valid` or automatic deserialization — we handle the XML parsing ourselves in the service using JAXB.

**What does `consumes = APPLICATION_XML_VALUE` do?**

It tells Spring: "This endpoint ONLY accepts requests with `Content-Type: application/xml`". If someone sends `application/json`, they'll get a `415 Unsupported Media Type` error.

**What does `produces = APPLICATION_XML_VALUE` do?**

It tells Spring: "This endpoint returns `Content-Type: application/xml`". The client knows the response is XML.

---

### Summary: What Happens If Each Block Fails?

| Block | Failure | Exception | HTTP Status |
|-------|---------|-----------|-------------|
| JAXB Unmarshal | Bad XML | `XmlProcessingException` | 400 |
| Card Validation | Invalid number | `InvalidCardException` | 400 |
| Expiry Validation | Expired card | `CardExpiredException` | 400 |
| CVV Validation | Wrong format | `InvalidCVVException` | 400 |
| Amount Validation | Out of range | `AmountLimitExceededException` | 400 |
| Currency Validation | Not supported | `UnsupportedCurrencyException` | 400 |
| Fraud Check | Risk too high | `FraudDetectedException` | 403 |
| Fraud Check | Service down | `FraudServiceUnavailableException` | 503 |
| Bank Auth | Declined | `PaymentDeclinedException` | 422 |
| Bank Auth | No money | `InsufficientFundsException` | 422 |
| Bank Auth | Timeout | `GatewayTimeoutException` | 504 |
| JAXB Marshal | Serialization fail | `XmlProcessingException` | 400 |

---

## 5. JAXB — XML Marshalling & Unmarshalling

### What is JAXB?

**JAXB** = Jakarta XML Binding (previously Java Architecture for XML Binding)

It converts between XML and Java objects:
```
Unmarshal:  XML String  →  Java Object  (reading XML)
Marshal:    Java Object →  XML String   (writing XML)
```

### Why isn't JAXB included by default?

In **Java 8**, JAXB was part of the JDK (`java.xml.bind` package).
In **Java 11+**, it was **removed from the JDK** (moved to Jakarta EE).
So we need to add it as a dependency:

```xml
<!-- JAXB API -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
</dependency>

<!-- JAXB Runtime (Glassfish implementation) -->
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
</dependency>
```

### Key JAXB Annotations:

```java
@XmlRootElement(name = "PaymentRequest")  // Marks class as XML root element
@XmlAccessorType(XmlAccessType.FIELD)     // Use fields directly (not getters)
@XmlElement(required = true)              // Marks a field as XML element
```

### How Unmarshalling Works (XML → Java):

```java
// Step 1: Create JAXB context for the target class
JAXBContext context = JAXBContext.newInstance(PaymentRequest.class);

// Step 2: Create unmarshaller
Unmarshaller unmarshaller = context.createUnmarshaller();

// Step 3: Parse XML string
StringReader reader = new StringReader(xmlString);
PaymentRequest request = (PaymentRequest) unmarshaller.unmarshal(reader);

// Now request.getCardNumber() returns "4111111111111111"
// Now request.getAmount() returns 1500.0
```

### How Marshalling Works (Java → XML):

```java
// Step 1: Create JAXB context
JAXBContext context = JAXBContext.newInstance(PaymentResponse.class);

// Step 2: Create marshaller
Marshaller marshaller = context.createMarshaller();

// Step 3: Enable pretty printing
marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

// Step 4: Convert to XML string
StringWriter writer = new StringWriter();
marshaller.marshal(responseObject, writer);
String xmlString = writer.toString();
```

### Important: JAXB Needs a No-Arg Constructor

```java
@Data
@NoArgsConstructor   // ← JAXB REQUIRES this! It creates empty object first, then sets fields
@AllArgsConstructor
@Builder
@XmlRootElement(name = "PaymentRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentRequest {
    // fields...
}
```

Without `@NoArgsConstructor`, JAXB will throw:
```
com.sun.xml.bind.v2.runtime.IllegalAnnotationsException:
1 counts of IllegalAnnotationExceptions
PaymentRequest does not have a no-arg default constructor
```

---

## 6. WebClient — Calling External Services

### What is WebClient?

WebClient is Spring's **modern HTTP client** (replacement for RestTemplate):

```
RestTemplate (legacy)     → Simple, synchronous, blocking
WebClient (modern)        → Reactive, non-blocking, supports sync & async
```

### Why do we need it?

Our service needs to call **two external APIs**:
1. **Fraud Detection Service** — checks if the transaction is suspicious
2. **Bank Authorization Service** — approves/declines the payment

### Dependency:

```xml
<!-- Spring Boot WebFlux (includes WebClient) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Configuration (WebClientConfig.java):

```java
@Configuration
public class WebClientConfig {

    @Value("${fraud.service.base-url:http://localhost:8081}")
    private String fraudServiceBaseUrl;

    @Value("${bank.service.base-url:http://localhost:8082}")
    private String bankServiceBaseUrl;

    @Bean(name = "fraudServiceClient")   // Named bean
    public WebClient fraudServiceClient() {
        return WebClient.builder()
                .baseUrl(fraudServiceBaseUrl)
                .build();
    }

    @Bean(name = "bankServiceClient")    // Named bean
    public WebClient bankServiceClient() {
        return WebClient.builder()
                .baseUrl(bankServiceBaseUrl)
                .build();
    }
}
```

### Injecting Named WebClient Beans:

```java
// Use @Qualifier to inject the correct WebClient bean by name
public PaymentGatewayServiceImpl(
        @Qualifier("fraudServiceClient") WebClient fraudServiceClient,
        @Qualifier("bankServiceClient") WebClient bankServiceClient) {
    this.fraudServiceClient = fraudServiceClient;
    this.bankServiceClient = bankServiceClient;
}
```

### Making a WebClient Call:

```java
FraudCheckResponse fraudResponse = fraudServiceClient
    .post()                                        // HTTP POST method
    .uri("/api/fraud/check")                       // Endpoint path
    .bodyValue(Map.of(                             // Request body
        "cardNumber", "****1111",
        "amount", 1500.0,
        "currency", "USD"
    ))
    .retrieve()                                    // Execute the request
    .bodyToMono(FraudCheckResponse.class)          // Convert response to Mono<T>
    .block();                                      // Block and get the result (sync)
```

### WebClient Chain Explained:

```
fraudServiceClient         ← The WebClient bean (has base URL configured)
    .post()                ← HTTP method (can be .get(), .put(), .delete())
    .uri("/api/fraud/check") ← Path appended to base URL
    .bodyValue(requestBody)  ← Request body (auto-serialized to JSON)
    .retrieve()              ← Sends the request, gets response
    .bodyToMono(Class)       ← Deserialize response body to this type
    .block()                 ← Convert from reactive Mono to sync result
```

### Why `.block()`?

WebClient is reactive by default (returns `Mono<T>`).
Since our service is **not reactive**, we use `.block()` to wait for the result synchronously.

```
Without .block():  Returns Mono<FraudCheckResponse>  (reactive/async)
With .block():     Returns FraudCheckResponse         (sync/blocking)
```

---

## 7. Business Logic Breakdown (12 Steps)

### Step 2: Validation — Luhn Algorithm

The **Luhn algorithm** validates credit card numbers. It's a checksum formula:

```
Card Number: 4 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1

Step 1: Starting from rightmost, double every second digit:
  4  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
  ↓     ↓     ↓     ↓     ↓     ↓     ↓     ↓
  8  1  2  1  2  1  2  1  2  1  2  1  2  1  2  1

Step 2: If doubled digit > 9, subtract 9:
  8  1  2  1  2  1  2  1  2  1  2  1  2  1  2  1
  (all ≤ 9, no changes)

Step 3: Sum all digits:
  8+1+2+1+2+1+2+1+2+1+2+1+2+1+2+1 = 30

Step 4: If sum % 10 == 0 → VALID ✓
  30 % 10 = 0 → VALID ✓
```

### Step 3: Card Type Detection

```java
"4111..."  → starts with 4  → VISA
"5500..."  → starts with 5  → MASTERCARD
"3714..."  → starts with 37 → AMEX
"3400..."  → starts with 34 → AMEX
"6011..."  → starts with 6  → DISCOVER
"9999..."  → other           → UNKNOWN
```

### Step 5: Processing Fee by Card Type

| Card Type   | Fee % | On $1000 |
|-------------|-------|----------|
| VISA        | 2.0%  | $20.00   |
| MASTERCARD  | 2.2%  | $22.00   |
| AMEX        | 2.5%  | $25.00   |
| DISCOVER    | 1.8%  | $18.00   |
| UNKNOWN     | 3.0%  | $30.00   |

### Step 9: Final Amount Formula

```
finalAmount = convertedAmount + processingFee - discount + tax

Example (USD, VISA, loyalty member, $1000):
  convertedAmount = 1000.00  (USD, no conversion)
  processingFee   = 20.00    (2.0% of 1000)
  discount        = 10.00    (1% loyalty, amount ≤ 2000)
  tax             = 19.80    (2% of (1000 - 10))
  ─────────────────────────────────────
  finalAmount     = 1000 + 20 - 10 + 19.80 = 1029.80
```

---

## 8. Exception Handling Strategy

### Exception → HTTP Status Code Mapping:

```
VALIDATION ERRORS → 400 Bad Request:
  InvalidCardException          → Card number invalid (Luhn, format)
  CardExpiredException          → Card expired or bad expiry date
  InvalidCVVException           → CVV wrong format
  UnsupportedCurrencyException  → Currency not supported
  AmountLimitExceededException  → Amount negative/zero/too high/bad decimals
  XmlProcessingException        → XML cannot be parsed or generated

FRAUD → 403 Forbidden:
  FraudDetectedException        → Risk score > 80

PAYMENT ISSUES → 422 Unprocessable Entity:
  PaymentDeclinedException      → Bank declined the payment
  InsufficientFundsException    → Not enough money in account

EXTERNAL SERVICE ISSUES:
  FraudServiceUnavailableException → 503 Service Unavailable
  GatewayTimeoutException          → 504 Gateway Timeout
```

### Why Different Status Codes?

```
400 = "Your request is wrong"      → Client can fix and retry
403 = "You are not allowed"        → Transaction blocked, don't retry
422 = "Request is valid but can't process" → Valid card, but no money
503 = "Our dependency is down"     → Retry later
504 = "Our dependency timed out"   → Retry later
```

### Try-Catch Pattern in the Service:

We use a specific pattern for WebClient calls — catch specific exceptions first, then catch-all:

```java
try {
    // WebClient call...
    // Process response...
    // Throw business exceptions if needed (e.g., FraudDetectedException)
} catch (FraudDetectedException e) {
    throw e;  // Re-throw business exception as-is
} catch (WebClientResponseException e) {
    // HTTP errors from the external service (503, 500, etc.)
    throw new FraudServiceUnavailableException("Service error: " + e.getStatusCode());
} catch (Exception e) {
    // Connection refused, DNS failure, timeout, etc.
    if (e instanceof FraudDetectedException) throw (FraudDetectedException) e;
    throw new FraudServiceUnavailableException("Service unavailable: " + e.getMessage());
}
```

### Why do we re-throw `FraudDetectedException`?

Because our **business exception** is thrown **inside the try block**:

```java
try {
    FraudCheckResponse response = webClient.post()...block();
    if (response.getRiskScore() > 80) {
        throw new FraudDetectedException("...");  // ← thrown INSIDE try
    }
} catch (Exception e) {
    // This catches EVERYTHING — including our FraudDetectedException!
    // So we need to check and re-throw it
    if (e instanceof FraudDetectedException) throw (FraudDetectedException) e;
    throw new FraudServiceUnavailableException("...");
}
```

Without the re-throw, a fraud detection would incorrectly return "Service Unavailable" instead of "Fraud Detected".

### GlobalExceptionHandler Pattern:

We use a helper method to avoid repeating the same error response structure:

```java
@ExceptionHandler(InvalidCardException.class)
public ResponseEntity<Map<String, Object>> handleInvalidCard(InvalidCardException ex) {
    return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Card", ex.getMessage());
}

// Reusable helper method
private ResponseEntity<Map<String, Object>> buildErrorResponse(
        HttpStatus status, String error, String message) {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("timestamp", LocalDateTime.now().toString());
    errorBody.put("status", status.value());
    errorBody.put("error", error);
    errorBody.put("message", message);
    return ResponseEntity.status(status).body(errorBody);
}
```

---

## 9. All Classes & Their Purpose

### DTOs (Data Transfer Objects):

| Class | Purpose | Annotations |
|-------|---------|-------------|
| `PaymentRequest` | Incoming XML → Java object | `@XmlRootElement`, `@XmlElement` |
| `PaymentResponse` | Java object → Outgoing XML | `@XmlRootElement`, `@XmlElement` |
| `FraudCheckResponse` | Response from Fraud Service (JSON) | `@Data` only |
| `BankAuthResponse` | Response from Bank Service (JSON) | `@Data` only |

### Why PaymentRequest/Response have JAXB annotations but FraudCheckResponse/BankAuthResponse don't?

```
PaymentRequest/Response    → XML communication with client (needs JAXB)
FraudCheckResponse         → JSON communication with Fraud Service (WebClient handles JSON automatically)
BankAuthResponse           → JSON communication with Bank Service (WebClient handles JSON automatically)
```

### Exceptions (11 custom):

| Exception | HTTP Status | When Thrown |
|-----------|-------------|-------------|
| `InvalidCardException` | 400 | Card number fails Luhn or format |
| `CardExpiredException` | 400 | Card expired or invalid month |
| `InvalidCVVException` | 400 | CVV wrong length |
| `UnsupportedCurrencyException` | 400 | Currency not in supported list |
| `AmountLimitExceededException` | 400 | Amount ≤ 0, > 100000, or > 2 decimals |
| `XmlProcessingException` | 400 | JAXB marshal/unmarshal failure |
| `FraudDetectedException` | 403 | Risk score > 80 |
| `FraudServiceUnavailableException` | 503 | Fraud service down/error |
| `PaymentDeclinedException` | 422 | Bank declines payment |
| `InsufficientFundsException` | 422 | Bank says not enough money |
| `GatewayTimeoutException` | 504 | Bank service timeout/unreachable |

### Config:

| Class | Purpose |
|-------|---------|
| `WebClientConfig` | Creates two named WebClient beans (`fraudServiceClient`, `bankServiceClient`) |

### Service:

| Class | Purpose |
|-------|---------|
| `PaymentGatewayService` | Interface — defines `processPayment(String xmlRequest)` |
| `PaymentGatewayServiceImpl` | Full implementation with 12 steps, validations, WebClient calls |

### Controller:

| Class | Purpose |
|-------|---------|
| `PaymentGatewayController` | `POST /api/payment/process` — accepts XML, returns XML |

---

## 10. Testing Strategy — How We Tested Everything

### Why @ExtendWith for Service Tests?

```
Service depends on:
  → WebClient fraudServiceClient
  → WebClient bankServiceClient

Both are EXTERNAL services we don't own.
We need to MOCK them, not actually call them.

@ExtendWith(MockitoExtension.class) lets us:
  ✓ Mock both WebClient beans
  ✓ Control what the external services "return"
  ✓ Simulate errors (timeout, 503, connection refused)
  ✓ Test all branches without any real HTTP calls
  ✓ Tests run in milliseconds (no network)
```

### Why @WebMvcTest for Controller Tests?

```
Controller only depends on:
  → PaymentGatewayService (interface)

@WebMvcTest(PaymentGatewayController.class) lets us:
  ✓ Test HTTP layer (status codes, content types)
  ✓ Test exception handler mappings
  ✓ Mock the service with @MockBean
  ✓ Verify request/response format
  ✓ No real service logic runs
```

### Mocking WebClient — The Chain Pattern:

WebClient uses a **fluent builder chain** that we must mock step by step:

```java
// Real code calls:
webClient.post().uri("/api/...").bodyValue(body).retrieve().bodyToMono(Class).block()

// We need to mock EVERY step in the chain:
@Mock WebClient webClient;
@Mock WebClient.RequestBodyUriSpec requestBodyUriSpec;
@Mock WebClient.RequestBodySpec requestBodySpec;
@Mock WebClient.RequestHeadersSpec requestHeadersSpec;
@Mock WebClient.ResponseSpec responseSpec;

// Setup the chain:
when(webClient.post()).thenReturn(requestBodyUriSpec);
when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
when(responseSpec.bodyToMono(FraudCheckResponse.class))
    .thenReturn(Mono.just(mockResponse));  // ← Control what's "returned"
```

### Why Mono.just() and Mono.error()?

WebClient returns reactive types. In tests:

```java
// Simulate successful response:
Mono.just(BankAuthResponse.builder().status("APPROVED").build())

// Simulate error response:
Mono.error(WebClientResponseException.create(503, "Service Unavailable", null, null, null))

// Simulate empty response (null):
Mono.empty()  // .block() returns null
```

---

## 11. Test Categories & What They Cover

### @ExtendWith Tests (50 tests) — Service Logic:

| Category | Tests | What They Cover |
|----------|-------|-----------------|
| Happy Path | 5 | USD, EUR, GBP, INR payments, fraud check clear |
| Loyalty Discount | 4 | 5% (>5000), 3% (>2000), 1% (≤2000), no discount |
| Card Type Detection | 4 | VISA, MASTERCARD, AMEX, DISCOVER + fees |
| Card Validation | 4 | Null, letters, Luhn failure, too short |
| Expiry Date | 4 | Expired, too far future, month=0, month=13 |
| CVV Validation | 3 | Null, short CVV (VISA), 3-digit on AMEX |
| Amount Validation | 4 | Negative, zero, > limit, > 2 decimals |
| Currency Validation | 2 | Unsupported (BTC), null |
| XML Processing | 3 | Invalid tag, malformed, empty string |
| Fraud Detection | 5 | Skip (<1000), clear, manual review, high risk, service down |
| Bank Authorization | 7 | Declined, insufficient funds, null, unexpected status, timeout, HTTP error, generic error |
| Calculations | 3 | With discount, without discount, EUR conversion |
| Card Masking | 2 | Normal masking, short number |

### @WebMvcTest Tests (12 tests) — HTTP Layer:

| Test | Status | Validates |
|------|--------|-----------|
| Success | 200 | XML response returned |
| Invalid XML | 400 | XmlProcessingException → error body |
| Invalid Card | 400 | InvalidCardException → error body |
| Card Expired | 400 | CardExpiredException → error body |
| Invalid CVV | 400 | InvalidCVVException → error body |
| Unsupported Currency | 400 | UnsupportedCurrencyException → error body |
| Amount Exceeded | 400 | AmountLimitExceededException → error body |
| Fraud Detected | 403 | FraudDetectedException → error body |
| Fraud Unavailable | 503 | FraudServiceUnavailableException → error body |
| Payment Declined | 422 | PaymentDeclinedException → error body |
| Insufficient Funds | 422 | InsufficientFundsException → error body |
| Gateway Timeout | 504 | GatewayTimeoutException → error body |

---

## 12. Key Patterns Used in Tests

### Pattern 1: XML Helper Methods

Instead of writing raw XML strings in every test, we use builder helpers:

```java
// Full control helper:
private String buildPaymentXml(String cardNumber, String cardHolderName,
    int expiryMonth, int expiryYear, String cvv, double amount,
    String currency, String merchantId, boolean loyaltyMember, String description)

// Quick helper with defaults (VISA card, John Doe):
private String buildValidPaymentXml(double amount, String currency, boolean loyaltyMember)

// Simplest helper (500 USD, no loyalty):
private String buildValidPaymentXml()
```

### Pattern 2: @Nested Test Classes

Tests are organized by **category** using `@Nested`:

```java
@Nested
@DisplayName("Card Number Validation")
class CardValidationTests {
    @Test void nullCardNumber() { ... }
    @Test void cardNumberWithLetters() { ... }
    @Test void cardNumberFailsLuhn() { ... }
}

@Nested
@DisplayName("Fraud Detection Service")
class FraudCheckTests {
    @Test void skipFraudCheck_under1000() { ... }
    @Test void fraudDetected_highRisk() { ... }
}
```

### Pattern 3: assertThatThrownBy for Exception Testing

```java
// Instead of try-catch in tests, use AssertJ:
assertThatThrownBy(() -> paymentGatewayService.processPayment(xml))
    .isInstanceOf(InvalidCardException.class)           // Correct exception type
    .hasMessageContaining("Luhn validation");           // Correct error message
```

### Pattern 4: verify() — Checking What Was (Not) Called

```java
// Verify fraud service was NOT called for small amounts:
verify(fraudServiceClient, never()).post();

// Verify bank service WAS called:
verify(bankServiceClient).post();
```

---

## 13. Common Interview Questions This Covers

### Q: What is JAXB and how do you use it in Spring Boot 3?
**A:** JAXB (Jakarta XML Binding) converts between XML and Java objects. Since Java 11 removed it from the JDK, we add `jakarta.xml.bind-api` and `jaxb-runtime` as dependencies. We annotate classes with `@XmlRootElement` and `@XmlElement`, then use `JAXBContext`, `Marshaller`, and `Unmarshaller` for conversions.

### Q: Why WebClient over RestTemplate?
**A:** RestTemplate is in maintenance mode. WebClient supports reactive/non-blocking operations, better error handling, and is the recommended approach in Spring 5+/Spring Boot 3.

### Q: How do you test code that calls external APIs?
**A:** Mock the WebClient using Mockito. Mock each step of the fluent chain (`post()`, `uri()`, `bodyValue()`, `retrieve()`, `bodyToMono()`). Use `Mono.just()` for success, `Mono.error()` for failures, and `Mono.empty()` for null responses.

### Q: How do you handle multiple exception types?
**A:** Create specific exception classes for each error scenario, map them to HTTP status codes in `@RestControllerAdvice` with `@ExceptionHandler` methods. Use a helper method to build consistent error response structure.

### Q: What is the Luhn algorithm?
**A:** A checksum formula used to validate credit card numbers. Starting from the rightmost digit, double every second digit, subtract 9 if > 9, sum all digits. If sum % 10 == 0, the number is valid.

### Q: How do you inject multiple beans of the same type?
**A:** Use `@Bean(name = "...")` in config to create named beans, and `@Qualifier("...")` in the constructor to inject the correct one.

### Q: What's the difference between @MockBean and @Mock?
**A:** `@Mock` (Mockito) creates a mock in plain unit tests (`@ExtendWith`). `@MockBean` (Spring) replaces a bean in the Spring Application Context, used with `@WebMvcTest` or `@SpringBootTest`.

---

## Quick Reference — Dependencies Added

```xml
<!-- WebClient (HTTP client for external service calls) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- JAXB API (XML ↔ Java conversion interface) -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
</dependency>

<!-- JAXB Runtime (actual implementation by Glassfish) -->
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
</dependency>
```

---

## File Structure

```
src/main/java/com/ecommerce/unittesting/
├── config/
│   └── WebClientConfig.java              ← WebClient beans for fraud + bank services
├── controller/
│   └── PaymentGatewayController.java     ← POST /api/payment/process
├── dto/
│   ├── PaymentRequest.java               ← JAXB XML model (input)
│   ├── PaymentResponse.java              ← JAXB XML model (output)
│   ├── FraudCheckResponse.java           ← Fraud service JSON response
│   └── BankAuthResponse.java             ← Bank service JSON response
├── exception/
│   ├── GlobalExceptionHandler.java       ← Maps exceptions → HTTP status codes
│   ├── InvalidCardException.java         ← 400: Card number invalid
│   ├── CardExpiredException.java         ← 400: Card expired
│   ├── InvalidCVVException.java          ← 400: CVV invalid
│   ├── UnsupportedCurrencyException.java ← 400: Currency not supported
│   ├── AmountLimitExceededException.java ← 400: Amount out of range
│   ├── XmlProcessingException.java       ← 400: XML parse/generate failure
│   ├── FraudDetectedException.java       ← 403: Fraud risk too high
│   ├── FraudServiceUnavailableException.java ← 503: Fraud service down
│   ├── PaymentDeclinedException.java     ← 422: Bank declined
│   ├── InsufficientFundsException.java   ← 422: Not enough funds
│   └── GatewayTimeoutException.java      ← 504: Bank service timeout
└── service/
    ├── PaymentGatewayService.java        ← Interface
    └── impl/
        └── PaymentGatewayServiceImpl.java ← 12-step business logic

src/test/java/com/ecommerce/unittesting/
├── extendwith/
│   └── PaymentGatewayServiceTest.java    ← 50 unit tests (mocked WebClient)
└── webmvctest/
    └── PaymentGatewayControllerTest.java ← 12 controller tests (mocked service)
```
