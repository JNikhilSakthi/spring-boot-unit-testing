# 100% Code Coverage Checklist

> After reading this document, you should be able to look at ANY Spring Boot code
> and immediately know what tests to write for 100% line + branch coverage.

---

## How to Use This Document

1. Open any source file
2. Read it line by line
3. For each line, check this document to see what test pattern applies
4. Write the test
5. Repeat until JaCoCo shows green everywhere

---

## The Master Checklist

Print this. Pin it. Use it every time you write tests.

```
WHEN YOU SEE THIS IN CODE              →  WRITE THESE TESTS
─────────────────────────────────────────────────────────────────

□  if (condition)                       →  Test condition=TRUE and condition=FALSE
□  if (a || b)                          →  Test a=true (short-circuits), a=false+b=true, both=false
□  if (a && b)                          →  Test both=true, a=false (short-circuits), a=true+b=false
□  if/else if/else                      →  One test per branch (N branches = N tests)
□  ternary (x ? a : b)                  →  Test x=true (gets a) and x=false (gets b)

□  try { ... } catch (E e)             →  One test where try succeeds + one where catch triggers
□  try with MULTIPLE catches            →  One test per catch block (each needs different exception)
□  catch that re-throws                 →  Verify the original exception comes out, not a wrapped one
□  catch that logs and swallows         →  Verify the method continues/returns default value
□  finally block                        →  Verify it runs in BOTH success and failure paths
□  nested try/catch                     →  Each inner and outer catch needs its own trigger

□  switch (value) { case A: case B: }   →  One test per case + one for default
□  switch expression (Java 14+)         →  Same — every arrow case + default

□  .orElseThrow()                       →  Test Optional.of(value) AND Optional.empty()
□  .orElse(default)                     →  Test when value present AND when absent
□  Optional.isPresent() / isEmpty()     →  Test both present and absent

□  for/while loop                       →  Test 0 iterations, 1 iteration, N iterations
□  .stream().map().collect()            →  Test with data (lambda executes) + empty list (lambda skipped)
□  .stream().filter()                   →  Test items that pass filter + items that don't + empty

□  null check (if x == null)            →  Test with null AND non-null
□  .isBlank() / .isEmpty()              →  Test null, empty "", blank "   ", and valid string
□  collection.isEmpty()                 →  Test with empty and non-empty collection

□  External API call (WebClient/REST)   →  Success + HTTP error + timeout + connection refused + null response
□  @Async / CompletableFuture           →  Test completion + exception + timeout
□  @Scheduled                           →  Test the method logic directly (ignore scheduling)
□  Kafka producer                       →  Test send success + serialization failure
□  Kafka consumer                       →  Test each event type + malformed message + processing error

□  @ExceptionHandler                    →  Trigger each handler, verify HTTP status + response body
□  @Valid / validation annotations      →  Test valid input + each invalid field
□  Custom validation                    →  Test each validation rule passes and fails

□  @PreAuthorize / @Secured             →  Test with correct role + wrong role + no auth
□  SecurityFilterChain                  →  Test each URL pattern: permitted + authenticated + forbidden
□  .with(jwt()) in tests                →  Test with JWT + without JWT (unauthenticated)

□  Boundary values                      →  0, -1, MAX_VALUE, empty string, null, single item, max items
□  Equality thresholds (> vs >=)        →  Test exactly AT the boundary: if (x > 100), test 100 and 101
```

---

## Pattern-by-Pattern — With Real Examples from Payment Gateway

### Pattern 1: if/else

**Source code:**
```java
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
```

**Checklist:** 5 branches → 5 tests minimum

**Tests:**
```java
// Branch 1: starts with "4"
@Test void shouldDetectVisa() {
    String xml = buildPaymentXml("4111111111111111", ...);
    String result = service.processPayment(xml);
    assertThat(result).contains("<cardType>VISA</cardType>");
}

// Branch 2: starts with "5"
@Test void shouldDetectMastercard() {
    String xml = buildPaymentXml("5500000000000004", ...);
    ...
}

// Branch 3a: starts with "37"
@Test void shouldDetectAmex37() { ... }

// Branch 3b: starts with "34" — IMPORTANT: covers the OTHER side of ||
@Test void shouldDetectAmex34() { ... }

// Branch 4: starts with "6"
@Test void shouldDetectDiscover() { ... }

// Branch 5: default — starts with "9"
@Test void shouldDetectUnknown() { ... }
```

**Common mistake:** Forgetting the `default`/`else` branch. JaCoCo will show it as uncovered.

---

### Pattern 2: if (a || b) — Short-Circuit Evaluation

**Source code:**
```java
if (cardNumber == null || cardNumber.isBlank()) {
    throw new InvalidCardException("Card number is required");
}
```

**How || works in Java:**
- If `a` is true → `b` is NEVER evaluated (short-circuit)
- If `a` is false → `b` IS evaluated

**JaCoCo tracks BOTH sides** of `||`. For 100% branch coverage, you need:

| Test | a (null) | b (isBlank) | Result |
|------|----------|-------------|--------|
| `null` input | TRUE | not evaluated | Exception thrown |
| `"   "` input | FALSE | TRUE | Exception thrown |
| `"4111..."` input | FALSE | FALSE | Passes validation |

**Tests:**
```java
@Test void nullCardNumber() {
    // a=true, short-circuits, b never called
    String xml = buildPaymentXml(null, ...);
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(InvalidCardException.class)
            .hasMessageContaining("required");
}

@Test void blankCardNumber() {
    // a=false (not null), b=true (is blank)
    String xml = buildPaymentXml("   ", ...);
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(InvalidCardException.class)
            .hasMessageContaining("required");
}

// Happy path covers both=false
```

**Common mistake:** Only testing `null` and thinking the `||` is fully covered. JaCoCo shows yellow (partial branch) because `.isBlank()` was never the deciding factor.

---

### Pattern 3: try/catch with Multiple Catch Blocks

**Source code:**
```java
try {
    FraudCheckResponse response = fraudServiceClient.post()...block();
    // process response...
} catch (FraudDetectedException e) {
    throw e;                              // Catch A: re-throw
} catch (WebClientResponseException e) {
    throw new FraudServiceUnavailableException(...);  // Catch B: HTTP error
} catch (Exception e) {
    throw new FraudServiceUnavailableException(...);  // Catch C: generic error
}
```

**Rule:** Each catch block needs its own test that triggers THAT SPECIFIC catch.

**Tests:**
```java
// Catch A: FraudDetectedException — thrown inside try, re-thrown by catch
@Test void fraudCheck_highRisk() {
    mockFraudCheck(90, "FRAUDULENT");  // riskScore > 80 → throws FraudDetectedException inside try
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(FraudDetectedException.class);
}

// Catch B: WebClientResponseException — fraud service returns HTTP 503
@Test void fraudService_webClientError() {
    when(responseSpec.bodyToMono(FraudCheckResponse.class))
            .thenReturn(Mono.error(WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(FraudServiceUnavailableException.class);
}

// Catch C: Exception — generic error (connection refused)
@Test void fraudService_genericError() {
    when(responseSpec.bodyToMono(FraudCheckResponse.class))
            .thenReturn(Mono.error(new RuntimeException("Connection refused")));
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(FraudServiceUnavailableException.class)
            .hasMessageContaining("Connection refused");
}
```

**Why the re-throw catch (Catch A)?** Without it, `FraudDetectedException` would be caught by `catch (Exception e)` and wrapped as `FraudServiceUnavailableException`. The re-throw ensures the RIGHT exception reaches the controller.

**Common mistake:** Thinking "I tested an exception, so all catches are covered." NO — each catch needs a DIFFERENT exception type to trigger it.

---

### Pattern 4: switch (Every Case + Default)

**Source code:**
```java
double feePercentage = switch (cardType) {
    case "VISA" -> 0.020;
    case "MASTERCARD" -> 0.022;
    case "AMEX" -> 0.025;
    case "DISCOVER" -> 0.018;
    default -> 0.030;
};
```

**Tests:** One test per case.

```java
@Test void visa_2percentFee() {
    // Use a card starting with "4" → cardType = "VISA"
    assertThat(result).contains("<processingFee>20.0</processingFee>");  // 2% of 1000
}

@Test void mastercard_2point2percentFee() { ... }
@Test void amex_2point5percentFee() { ... }
@Test void discover_1point8percentFee() { ... }
@Test void unknown_3percentDefaultFee() { ... }  // ← DON'T FORGET DEFAULT
```

**Common mistake:** Forgetting the `default` case. It's often never hit in normal usage, but JaCoCo marks it as uncovered.

---

### Pattern 5: .orElseThrow()

**Source code (from UserService):**
```java
User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
```

**Always 2 tests:**

```java
// Test 1: Found → orElseThrow does NOT throw
@Test void getUserById_found() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    UserResponse result = userService.getUserById(1L);
    assertNotNull(result);
}

// Test 2: Not found → orElseThrow DOES throw
@Test void getUserById_notFound() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
}
```

**Common mistake:** Only testing the happy path. The lambda inside `orElseThrow()` is a branch — if you never trigger it, JaCoCo shows partial coverage.

---

### Pattern 6: Stream/Lambda — Empty Collection

**Source code (from UserService):**
```java
return userRepository.findAll()
        .stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
```

**Why test empty?** The `.map()` lambda body ONLY executes if there are items. With an empty list, the lambda is never called → JaCoCo may show the lambda as uncovered.

```java
// Test 1: With data — lambda executes
@Test void getAllUsers_returnsList() {
    when(userRepository.findAll()).thenReturn(List.of(user1, user2));
    List<UserResponse> result = userService.getAllUsers();
    assertEquals(2, result.size());
}

// Test 2: Empty — lambda never executes, but .collect() still runs
@Test void getAllUsers_empty() {
    when(userRepository.findAll()).thenReturn(List.of());
    List<UserResponse> result = userService.getAllUsers();
    assertTrue(result.isEmpty());
}
```

---

### Pattern 7: External API Call — All Failure Modes

**Source code:**
```java
BankAuthResponse bankResponse = bankServiceClient.post()
        .uri("/api/bank/authorize")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(BankAuthResponse.class)
        .block();
```

**Test every failure mode:**

| Failure | How to Trigger | Exception |
|---------|---------------|-----------|
| Success | `Mono.just(approvedResponse)` | None — happy path |
| Null response | `Mono.empty()` | GatewayTimeoutException |
| HTTP 504 | `Mono.error(WebClientResponseException.create(504, ...))` | GatewayTimeoutException |
| HTTP 500 | `Mono.error(WebClientResponseException.create(500, ...))` | PaymentDeclinedException |
| Connection refused | `Mono.error(new RuntimeException("Connection refused"))` | GatewayTimeoutException |
| DECLINED response | `Mono.just(declinedResponse)` | PaymentDeclinedException |
| INSUFFICIENT_FUNDS | `Mono.just(declinedWithReason)` | InsufficientFundsException |

---

### Pattern 8: MockedStatic — Covering "Unreachable" Catch Blocks

Sometimes a catch block exists for safety but can never trigger in normal code. Example: marshalling a valid Java object to XML should NEVER fail.

```java
try {
    JAXBContext context = JAXBContext.newInstance(PaymentResponse.class);
    // This CANNOT fail in normal code — PaymentResponse has valid @Xml annotations
    Marshaller marshaller = context.createMarshaller();
    marshaller.marshal(response, writer);
} catch (JAXBException e) {
    // How do we cover THIS line if it never fails?
    throw new XmlProcessingException("Failed to generate XML response");
}
```

**Solution: MockedStatic**

```java
@Test void marshalFailure() throws Exception {
    JAXBContext realContext = JAXBContext.newInstance(PaymentRequest.class);
    // Save real context before mocking

    try (MockedStatic<JAXBContext> mockedJaxb = mockStatic(JAXBContext.class)) {
        // Let unmarshal work normally
        mockedJaxb.when(() -> JAXBContext.newInstance(PaymentRequest.class))
                .thenReturn(realContext);

        // Make marshal FAIL
        mockedJaxb.when(() -> JAXBContext.newInstance(PaymentResponse.class))
                .thenThrow(new JAXBException("forced failure"));

        assertThatThrownBy(() -> service.processPayment(xml))
                .isInstanceOf(XmlProcessingException.class);
    }
}
```

**When to use MockedStatic:**
- `JAXBContext.newInstance()` — static factory method
- `LocalDateTime.now()` — static time method
- `UUID.randomUUID()` — static random generation
- Any `static` method you need to force a specific behavior

**Important:** `try-with-resources` ensures the static mock is cleaned up after the test. Without it, other tests would break because `JAXBContext` would still be mocked.

---

### Pattern 9: @ExceptionHandler — Trigger Each One

**Source code (GlobalExceptionHandler):**
```java
@ExceptionHandler(InvalidCardException.class)
public ResponseEntity<Map<String, Object>> handleInvalidCard(InvalidCardException ex) {
    return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Card", ex.getMessage());
}
```

**How to test:** In controller tests, make the mocked service throw each exception:

```java
@Test void invalidCard_returns400() throws Exception {
    when(service.processPayment(anyString()))
            .thenThrow(new InvalidCardException("Card number failed Luhn validation"));

    mockMvc.perform(post("/api/payment/process")
                    .contentType(MediaType.APPLICATION_XML)
                    .content(xml))
            .andExpect(status().isBadRequest())                    // Verify HTTP status
            .andExpect(jsonPath("$.error").value("Invalid Card"))  // Verify error label
            .andExpect(jsonPath("$.message").value("Card number failed Luhn validation"));
}
```

**Each exception handler needs its own test.** If you have 11 exception handlers, you need 11 controller tests (minimum).

---

### Pattern 10: Boundary Values

**Source code:**
```java
if (amount <= 0) { throw ... }
if (amount > MAX_TRANSACTION_AMOUNT) { throw ... }
```

**Test AT the boundaries:**

| Value | Which branch? | Why? |
|-------|--------------|------|
| -100 | `<= 0` TRUE | Negative |
| 0 | `<= 0` TRUE | Exactly zero |
| 0.01 | `<= 0` FALSE | Just above zero (smallest valid) |
| 100000.0 | `> 100000` FALSE | Exactly at max (still valid) |
| 100000.01 | `> 100000` TRUE | Just above max (invalid) |
| 150000 | `> 100000` TRUE | Well above max |

**Common mistake:** Only testing -100 and 150000. Missing the boundary itself (0 and 100000) where off-by-one bugs hide. `> 100000` vs `>= 100000` is a common bug — boundary tests catch it.

---

### Pattern 11: Nested if/else (Decision Tree)

**Source code:**
```java
if (paymentRequest.isLoyaltyMember()) {        // Level 1
    if (convertedAmount > 5000) {               // Level 2a
        discount = convertedAmount * 0.05;
    } else if (convertedAmount > 2000) {        // Level 2b
        discount = convertedAmount * 0.03;
    } else {                                     // Level 2c
        discount = convertedAmount * 0.01;
    }
}
// Implicit else: discount stays 0.0            // Level 1 false
```

**Draw the decision tree:**
```
isLoyaltyMember?
├── NO  → discount = 0.0                    ← Test 1
└── YES
    ├── amount > 5000? → 5% discount         ← Test 2
    ├── amount > 2000? → 3% discount         ← Test 3
    └── else           → 1% discount         ← Test 4
```

**4 leaf nodes = 4 tests.** Count the LEAVES of the decision tree, not the nodes.

---

## The 3-Step Process for Any New Code

### Step 1: Scan for Branches

Read the code and mark every:
- `if` / `else if` / `else`
- `try` / `catch` (each catch separately)
- `switch` / `case` / `default`
- `? :` (ternary)
- `||` and `&&` (each side)
- `.orElseThrow()` / `.orElse()`
- Stream operations with lambdas

### Step 2: Draw the Coverage Map

For each method, list every path:

```
processPayment():
  Path 1: invalid XML → catch JAXBException → XmlProcessingException
  Path 2: null card → InvalidCardException
  Path 3: blank card → InvalidCardException
  Path 4: card fails regex → InvalidCardException
  Path 5: card fails Luhn → InvalidCardException
  Path 6: expired card → CardExpiredException
  ...
  Path N: all valid → SUCCESS response
```

### Step 3: Write One Test Per Path

Each path = one `@Test` method. Name it clearly:

```
methodName_scenario_expectedResult

processPayment_nullCardNumber_throwsInvalidCardException
processPayment_amountOver1000_fraudCheckClear_returns200
processPayment_bankDeclined_insufficientFunds_throwsInsufficientFundsException
```

---

## JaCoCo Color Guide

After running tests, open `target/site/jacoco/index.html`:

| Color | Meaning | Action |
|-------|---------|--------|
| **Green** | Line fully covered | Nothing to do |
| **Yellow** | Line partially covered (some branches missed) | Check which branch is untested |
| **Red** | Line never executed | Write a test that hits this line |

**Yellow is the tricky one.** It usually means:
- `if (a || b)` — only tested one side of `||`
- `if/else` — only tested one branch
- `try/catch` — only tested happy path, not the catch
- Ternary `? :` — only tested one outcome

---

## Quick Reference — Exception Trigger Techniques

| What You Need | Mockito Technique |
|--------------|-------------------|
| Method returns wrong value | `when(mock.method()).thenReturn(badValue)` |
| Method throws checked exception | `when(mock.method()).thenThrow(new IOException())` |
| Method throws unchecked exception | `when(mock.method()).thenThrow(new RuntimeException())` |
| Void method throws | `doThrow(new Exception()).when(mock).method()` |
| Optional returns empty | `when(repo.findById(id)).thenReturn(Optional.empty())` |
| WebClient returns error | `Mono.error(WebClientResponseException.create(503, ...))` |
| WebClient returns empty | `Mono.empty()` (for null response) |
| Static method throws | `mockStatic(Class.class)` + `.thenThrow(...)` |
| Collection is empty | `when(repo.findAll()).thenReturn(List.of())` |
| String is null | Pass `null` directly in test input |
| String is blank | Pass `"   "` in test input |

---

## Checklist — Before Submitting Any PR

```
□  Every if/else branch tested (both true and false)
□  Every || and && has all sides tested
□  Every try/catch has happy path + every catch block triggered
□  Every switch case covered + default
□  Every .orElseThrow() tested found + not-found
□  Every stream/lambda tested with data + empty
□  Every external call tested: success + error + timeout + null
□  Every @ExceptionHandler triggered in controller tests
□  Every validation rule tested: valid + each invalid case
□  Boundary values tested: 0, -1, MAX, empty, null, blank
□  JaCoCo report shows 100% line and branch coverage
□  No yellow lines in JaCoCo (partial branch coverage)
```
