# Mental Model — How to Read Any Code and Know Exactly What Tests to Write

> This is the thinking framework. After internalizing this, you should be able to
> look at ANY Spring Boot code and instantly see the tests it needs.

---

## The One Rule

**Every line of code either ALWAYS executes, or SOMETIMES executes.**

- Lines that ALWAYS execute → covered by any test that reaches that method
- Lines that SOMETIMES execute → need a specific test that makes them execute

Your job: find every "sometimes" line and write a test for it.

---

## The 5-Second Scan

When you first open a file, do a quick visual scan for these keywords. Each one is a signal that says "I need multiple tests":

```
KEYWORD          SIGNAL                              MINIMUM TESTS
───────────────────────────────────────────────────────────────────
if               Conditional branch                  2 (true + false)
else             Alternative branch                  1 (dedicated test for this path)
else if          Another branch                      1 per else-if
switch           Multiple paths                      1 per case + default
case             One path in switch                  1
default          Fallback path                       1 (often forgotten!)
? :              Ternary — hidden if/else            2 (true + false)
try              Protected block                     1 (happy path)
catch            Error handler                       1 per catch block
finally          Cleanup                             Verify runs in both paths
throw            Exception being thrown              1 (trigger this throw)
||               OR — two conditions                 2 (left=true, left=false+right=true)
&&               AND — two conditions                2 (both=true, either=false)
!                Negation                            2 (true input + false input)
return           Exit point in middle of method      1 (trigger early return)
.orElseThrow()   Optional unwrap                     2 (present + empty)
.orElse()        Optional with default               2 (present + empty)
.stream()        Collection processing               2 (with data + empty)
.filter()        Conditional inclusion               2+ (items that pass + items that don't)
for / while      Loop                                3 (0 iterations, 1, multiple)
break / continue Loop control                        1 per break/continue path
new              Object creation inside try           Might throw (checked exception)
```

---

## The Reading Framework — 4 Passes

### Pass 1: The Happy Path

Read the method from top to bottom assuming everything goes right.

For `processPayment()`:
```
1. XML parses successfully           ✓
2. Card number is valid              ✓
3. Expiry date is valid              ✓
4. CVV is valid                      ✓
5. Amount is valid                   ✓
6. Currency is valid                 ✓
7. Card type detected                ✓
8. Fraud check passes                ✓
9. Fee calculated                    ✓
10. Currency converted               ✓
11. Loyalty discount applied         ✓
12. Tax calculated                   ✓
13. Final amount computed            ✓
14. Bank approves                    ✓
15. Response built                   ✓
16. XML marshalled                   ✓
→ Return SUCCESS XML
```

**This is test #1 — the happy path.** Write it first.

### Pass 2: The Error Paths

Read the method again, but this time stop at every `throw`, `catch`, and early `return`.

```
Line 69:  catch (JAXBException)     → What makes XML invalid?
Line 241: throw InvalidCardException → What if card is null? blank? wrong format? fails Luhn?
Line 278: throw CardExpiredException → What if month is 0? 13? Date is past? Too far future?
Line 297: throw InvalidCVVException  → What if CVV is null? blank? wrong length? AMEX?
Line 316: throw AmountLimitExceeded  → What if amount is 0? negative? too high? too many decimals?
Line 337: throw UnsupportedCurrency  → What if currency is null? blank? "BTC"?
Line 104: throw FraudDetectedException → What if risk score > 80?
Line 115: throw FraudServiceUnavailable → What if fraud service returns 503? Connection refused?
Line 172: throw GatewayTimeoutException → What if bank returns null? times out?
Line 177: throw InsufficientFunds    → What if bank says "INSUFFICIENT_FUNDS"?
Line 180: throw PaymentDeclined      → What if bank says "DECLINED"?
Line 185: throw PaymentDeclined      → What if bank says "PENDING" (unexpected)?
Line 233: catch (JAXBException)      → What if marshalling fails? (MockedStatic needed)
```

**Each `throw` = at least one test.**

### Pass 3: The Conditional Variations

Read again, focusing on `if/else`, `switch`, and conditional logic.

```
Step 3 — determineCardType:
  if starts with "4"     → VISA
  else if starts with "5" → MASTERCARD
  else if starts with "34" or "37" → AMEX
  else if starts with "6" → DISCOVER
  else → UNKNOWN
  ▸ 5 tests (6 with both AMEX prefixes)

Step 4 — fraud check:
  if amount > 1000       → call fraud service
  else                   → skip fraud check
  ▸ 2 tests

  if riskScore > 80      → FRAUD
  else if riskScore > 50 → MANUAL_REVIEW
  else                   → CLEAR
  ▸ 3 tests

Step 5 — processing fee:
  switch: VISA / MASTERCARD / AMEX / DISCOVER / default
  ▸ 5 tests

Step 6 — currency:
  if NOT USD             → convert
  else                   → skip
  ▸ 2 tests (+ each currency for thoroughness)

Step 7 — loyalty:
  if loyaltyMember       → calculate discount
  else                   → skip
  ▸ if amount > 5000     → 5%
  ▸ else if > 2000       → 3%
  ▸ else                 → 1%
  ▸ 4 tests total

Step 10 — bank response:
  if null                → GatewayTimeoutException
  if DECLINED            → check reason
    if INSUFFICIENT_FUNDS → InsufficientFundsException
    else                  → PaymentDeclinedException
  if not APPROVED         → PaymentDeclinedException
  ▸ 5 tests for response handling + 3 for catch blocks
```

### Pass 4: The Edge Cases

Things that aren't branches in the code but matter for robustness:

```
- What if card number has spaces? ("4111 1111 1111 1111")
  → Code does replaceAll("\\s+", "") — covered if spaces in test input

- What if amount is exactly at threshold? (1000.0 exactly)
  → if (amount > FRAUD_CHECK_THRESHOLD) — 1000.0 is NOT > 1000, so skipped
  → Test: amount=1000.0 should NOT trigger fraud check
  → Test: amount=1000.01 SHOULD trigger fraud check

- What if CVV has leading zeros? ("001")
  → regex \\d{3} accepts this — no issue, but good to verify

- What if Luhn doubling produces exactly 9? (digit=9, doubled=18, 18-9=9)
  → Covered by real card numbers — "4111111111111111" has this case

- What about concurrent requests?
  → Out of scope for unit tests — that's integration/load testing
```

---

## Reading Patterns — Recognize These Instantly

### Pattern A: Validate-Then-Process

```java
validate(input);        // Throws if invalid
process(input);         // Only runs if validation passes
```

**Tests needed:** One test per validation failure + one test where all pass.

This is the most common pattern in the payment gateway. Steps 2a-2e are all validate-then-continue.

### Pattern B: Check-Then-Act

```java
if (repository.existsById(id)) {
    throw new DuplicateException("Already exists");
}
repository.save(entity);
```

**Tests needed:** exists=true (exception) + exists=false (saves).

### Pattern C: Fetch-Or-Fail

```java
Entity entity = repository.findById(id)
        .orElseThrow(() -> new NotFoundException("Not found: " + id));
return mapper.toResponse(entity);
```

**Tests needed:** found (returns response) + not found (throws).

### Pattern D: External-Call-With-Fallback

```java
try {
    Response response = externalService.call();
    if (response == null) { throw timeout; }
    if (response.isBad()) { throw declined; }
    return response.getData();
} catch (SpecificException e) { throw e; }
  catch (HttpException e) { throw serviceError; }
  catch (Exception e) { throw genericError; }
```

**Tests needed:** success + null response + bad response + each catch block.

This is Steps 4 and 10 in the payment gateway.

### Pattern E: Tiered-Calculation

```java
if (amount > TIER_3) {
    result = amount * RATE_3;
} else if (amount > TIER_2) {
    result = amount * RATE_2;
} else {
    result = amount * RATE_1;
}
```

**Tests needed:** One per tier + non-qualifying case.

This is Step 7 (loyalty discount).

### Pattern F: Type-Based-Dispatch

```java
switch (type) {
    case "A" -> doA();
    case "B" -> doB();
    default -> doDefault();
}
```

**Tests needed:** One per case + default.

This is Step 5 (processing fee).

---

## How to Count Required Tests

Formula: **count the leaf nodes in the method's decision tree.**

```
processPayment()
├── XML invalid? → YES → XmlProcessingException (leaf 1)
│                → NO ↓
├── Card null/blank? → YES → InvalidCardException (leaf 2)
│                    → NO ↓
├── Card bad format? → YES → InvalidCardException (leaf 3)
│                    → NO ↓
├── Card fails Luhn? → YES → InvalidCardException (leaf 4)
│                     → NO ↓
├── Month invalid? → YES → CardExpiredException (leaf 5)
│                  → NO ↓
├── Card expired? → YES → CardExpiredException (leaf 6)
│                 → NO ↓
├── Card too far future? → YES → CardExpiredException (leaf 7)
│                        → NO ↓
├── CVV null/blank? → YES → InvalidCVVException (leaf 8)
│                   → NO ↓
├── CVV wrong length? → YES → InvalidCVVException (leaf 9)
│                     → NO ↓
├── Amount <= 0? → YES → AmountLimitExceededException (leaf 10)
│                → NO ↓
├── Amount > max? → YES → AmountLimitExceededException (leaf 11)
│                 → NO ↓
├── Amount bad decimals? → YES → AmountLimitExceededException (leaf 12)
│                        → NO ↓
├── Currency null/blank? → YES → UnsupportedCurrencyException (leaf 13)
│                        → NO ↓
├── Currency unsupported? → YES → UnsupportedCurrencyException (leaf 14)
│                         → NO ↓
├── Amount > 1000?
│   ├── NO → skip fraud (leaf continues) ↓
│   └── YES → call fraud service
│       ├── fraud throws FraudDetected → re-throw (leaf 15)
│       ├── fraud throws WebClientError → FraudServiceUnavailable (leaf 16)
│       ├── fraud throws generic error → FraudServiceUnavailable (leaf 17)
│       ├── fraud returns null → skip, keep defaults ↓
│       ├── riskScore > 80 → FraudDetectedException (leaf 18)
│       ├── riskScore > 50 → MANUAL_REVIEW, continue ↓
│       └── riskScore <= 50 → CLEAR, continue ↓
├── Bank response null? → GatewayTimeoutException (leaf 19)
├── Bank DECLINED + INSUFFICIENT_FUNDS → InsufficientFundsException (leaf 20)
├── Bank DECLINED + other reason → PaymentDeclinedException (leaf 21)
├── Bank unexpected status → PaymentDeclinedException (leaf 22)
├── Bank WebClient 504 → GatewayTimeoutException (leaf 23)
├── Bank WebClient other error → PaymentDeclinedException (leaf 24)
├── Bank generic error → GatewayTimeoutException (leaf 25)
├── Marshal fails → XmlProcessingException (leaf 26)
└── ALL SUCCESS → return XML response (leaf 27... but with variations for card type, currency, loyalty)
```

**~27 leaf nodes = ~27 minimum tests.** The actual test file has 62 because:
- Card types: 6 tests (VISA, MC, AMEX x2, DISCOVER, UNKNOWN)
- Currencies: 7 tests (USD, EUR, GBP, INR, JPY, AUD, CAD)
- Loyalty tiers: 4 tests
- Calculation verification: 3 tests
- Card masking: 2 tests
- These multiply the happy path into multiple variations

---

## Decision Framework: Mock vs Real

| Use MOCK when... | Use REAL when... |
|-----------------|-----------------|
| Dependency is external (WebClient, Kafka) | Dependency is a pure function (mapper, calculator) |
| Dependency is slow (database, network) | Dependency is fast and has no side effects |
| You need to force specific behavior (errors) | You want to test the integration between components |
| You're writing a unit test | You're writing an integration test |
| The dependency has its own tests | The dependency is simple and untested |

**In Payment Gateway:**
- `WebClient` → MOCK (external services, need to test error scenarios)
- `JAXBContext` → REAL in happy path, MockedStatic for error path only
- `PaymentRequest.builder()` → REAL (just a POJO, no reason to mock)
- `BigDecimal` operations → REAL (pure math, no side effects)

---

## Anti-Patterns — What NOT to Do

### 1. Testing the Mock, Not the Code

```java
// BAD: This tests that Mockito works, not that your code works
@Test void bad_test() {
    when(repo.findById(1L)).thenReturn(Optional.of(user));
    Optional<User> result = repo.findById(1L);  // Calling the MOCK directly
    assertTrue(result.isPresent());               // Of course it's present — you told it to be!
}

// GOOD: Test your SERVICE, which USES the mock
@Test void good_test() {
    when(repo.findById(1L)).thenReturn(Optional.of(user));
    UserResponse result = userService.getUserById(1L);  // Calling YOUR code
    assertEquals("John", result.getFirstName());         // Verifying YOUR logic
}
```

### 2. One Giant Test Instead of Focused Tests

```java
// BAD: Tests 5 things, fails for unclear reason
@Test void processPayment_allValidations() {
    // test null card, then bad CVV, then expired, then amount, then currency...
}

// GOOD: One test per scenario
@Test void processPayment_nullCard_throwsInvalidCardException() { ... }
@Test void processPayment_badCVV_throwsInvalidCVVException() { ... }
@Test void processPayment_expiredCard_throwsCardExpiredException() { ... }
```

### 3. Not Verifying the Exception Message

```java
// OK but incomplete
assertThrows(InvalidCardException.class, () -> service.processPayment(xml));

// BETTER: Also verify the message
assertThatThrownBy(() -> service.processPayment(xml))
        .isInstanceOf(InvalidCardException.class)
        .hasMessageContaining("Luhn validation");
// This catches bugs where the WRONG InvalidCardException is thrown
```

### 4. Forgetting to Verify What Was NOT Called

```java
// GOOD: When card is invalid, verify bank was NEVER called
@Test void invalidCard_doesNotCallBank() {
    String xml = buildPaymentXml(null, ...);
    assertThatThrownBy(() -> service.processPayment(xml))
            .isInstanceOf(InvalidCardException.class);
    verify(bankServiceClient, never()).post();     // Bank should NOT be called
    verify(fraudServiceClient, never()).post();    // Fraud should NOT be called
}
```

### 5. Using Real External Services in Unit Tests

```java
// NEVER DO THIS in a unit test
WebClient realClient = WebClient.builder().baseUrl("http://real-bank.com").build();

// ALWAYS mock
@Mock WebClient bankServiceClient;
```

---

## Summary — The Mental Loop

Every time you see new code, run this loop:

```
1. READ the method top to bottom
2. MARK every branch (if/else, try/catch, switch, ternary, ||, &&)
3. COUNT the leaf nodes in the decision tree
4. WRITE one @Test per leaf node
5. ADD edge cases (null, empty, blank, boundary values)
6. RUN tests and check JaCoCo
7. FIX any yellow/red lines with additional tests
8. REPEAT until 100% green
```

That's it. No magic. Just discipline.
