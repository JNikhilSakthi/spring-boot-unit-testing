# Spring Boot Unit Testing — Complete Learning Plan

> **Stack**: Spring Boot 4.0.3 + Java 25 + Spring Security 7.x
> **Domain**: E-commerce (User, Product, Order, Payment, Notification, Inventory, Report)
> **Format**: Markdown docs + working code per phase
> **Approach**: Branch-per-phase, progressive accumulation

---

## Branch Strategy

```
main                 → Clean project skeleton (entities, services, no tests)
│
├── phase-01-foundations       → JUnit 5 basics + docs
├── phase-02-mockito           → Phase 1 + Mockito + docs
├── phase-03-test-slices       → Phase 1-2 + Spring test slices + docs
├── phase-04-service-coverage  → Phase 1-3 + 100% service coverage + docs
├── phase-05-complex-scenarios → Phase 1-4 + Kafka, WebClient, etc + docs
├── phase-06-exception-paths   → Phase 1-5 + error path coverage + docs
├── phase-07-jacoco-coverage   → Phase 1-6 + JaCoCo config + docs
├── phase-08-best-practices    → Phase 1-7 + refactored tests + docs
├── phase-09-advanced-libs     → Phase 1-8 + AssertJ, Testcontainers + docs
├── phase-10-microservice      → Phase 1-9 + contracts, Feign + docs
├── phase-11-security          → Phase 1-10 + security tests + docs (FINAL)
```

Each branch **accumulates** all previous phases. Checking out `phase-05` gives you phases 1-5.

### How to Use

```bash
# Start learning
git checkout phase-01-foundations
# Study docs/phase-01-foundations.md, read tests, run them, experiment

# Ready for next phase
git checkout phase-02-mockito
# Now you have Phase 1 tests + Phase 2 tests + new doc

# See exactly what a phase added
git diff phase-01-foundations..phase-02-mockito

# See only what's new in any phase
git diff phase-04-service-coverage..phase-05-complex-scenarios
```

---

## Project Structure

```
spring-boot-unit-testing/
├── docs/
│   ├── phase-01-foundations.md
│   ├── phase-02-mockito.md
│   ├── phase-03-test-slices.md
│   ├── phase-04-service-coverage.md
│   ├── phase-05-complex-scenarios.md
│   ├── phase-06-exception-paths.md
│   ├── phase-07-jacoco-coverage.md
│   ├── phase-08-best-practices.md
│   ├── phase-09-advanced-libs.md
│   ├── phase-10-microservice.md
│   └── phase-11-security.md
├── src/main/java/com/ecommerce/
│   ├── user/           → CRUD, validation, JPA, security, @ExceptionHandler
│   ├── product/        → Search, pagination, sorting, caching
│   ├── order/          → Complex business logic, state transitions, transactions
│   ├── payment/        → WebClient to external API, retry, circuit breaker, XML/JSON
│   ├── notification/   → Kafka producer/consumer, @Async, @Scheduled, email
│   ├── inventory/      → Concurrency, optimistic locking, cache eviction
│   └── report/         → File generation, upload/download, streaming
├── src/test/java/com/ecommerce/
│   ├── user/
│   ├── product/
│   ├── order/
│   ├── payment/
│   ├── notification/
│   ├── inventory/
│   └── report/
├── pom.xml
└── UNIT-TESTING-LEARNING-PLAN.md   ← this file
```

---

## Phase-by-Phase Curriculum

---

### Phase 1 — Foundations (JUnit 5)

**Branch**: `phase-01-foundations`

| Topic | Description |
|-------|-------------|
| Unit vs Integration vs E2E testing | What each level tests, when to use which |
| `@Test` | Basic test method annotation |
| `@BeforeEach` / `@AfterEach` | Setup/teardown before each test |
| `@BeforeAll` / `@AfterAll` | One-time setup/teardown for the class |
| `assertEquals` | Compare expected vs actual values |
| `assertNotNull` | Verify non-null results |
| `assertThrows` | Verify exceptions are thrown |
| `assertAll` | Group multiple assertions (all run even if one fails) |
| `assertTimeout` | Verify code completes within time limit |
| `@DisplayName` | Human-readable test names |
| `@Disabled` | Skip a test with reason |
| `@Nested` | Group related tests in inner classes |
| `@ParameterizedTest` | Run same test with different inputs |
| `@ValueSource` | Simple parameter values |
| `@CsvSource` | Comma-separated input/expected pairs |
| `@MethodSource` | Parameters from a factory method |
| `@EnumSource` | Test all enum values |
| `@RepeatedTest` | Repeat test N times for flaky detection |

**What you'll test**: Pure utility methods, validators, DTOs — no Spring context needed.

**Coverage contribution**: Basic line coverage of utility/helper classes.

---

### Phase 2 — Mocking with Mockito

**Branch**: `phase-02-mockito`

| Topic | Description |
|-------|-------------|
| `@Mock` | Create a mock object |
| `@InjectMocks` | Auto-inject mocks into the class under test |
| `@Spy` | Partial mock — real methods unless stubbed |
| `when().thenReturn()` | Stub a method to return a value |
| `when().thenThrow()` | Stub a method to throw an exception |
| `doNothing()` | Stub void methods to do nothing |
| `doThrow()` | Stub void methods to throw |
| `verify()` | Verify a method was called |
| `times()` / `never()` / `atLeastOnce()` | Verify call count |
| `ArgumentCaptor` | Capture arguments passed to mocked methods |
| `any()` / `eq()` / `argThat()` | Argument matchers |
| Mocking void methods | `doNothing().when(mock).method()` |
| Mocking static methods | `mockStatic(ClassName.class)` |
| Mocking private methods | Why you shouldn't + workarounds |
| `@MockitoBean` | Spring Boot 4.x replacement for `@MockBean` |

**What you'll test**: Service methods with mocked repositories — no database needed.

**Coverage contribution**: Service layer line coverage without Spring context overhead.

---

### Phase 3 — Spring Boot Test Slices

**Branch**: `phase-03-test-slices`

| Topic | Description |
|-------|-------------|
| **@SpringBootTest** | |
| Full context loading | When and why to use full context |
| `webEnvironment = RANDOM_PORT` | Testing with real HTTP |
| **@WebMvcTest** | |
| MockMvc setup | Controller-only tests, no server |
| GET endpoints | `mockMvc.perform(get("/api/..."))` |
| POST endpoints | `.content(json).contentType(APPLICATION_JSON)` |
| PUT / PATCH / DELETE | Full CRUD controller testing |
| `@Valid` errors | Test 400 responses for invalid input |
| Path variables / Query params | `get("/api/users/{id}", 1)` |
| Headers | Custom header testing |
| `.with(jwt())` | Spring Security 7.x auth in tests |
| `@Import(SecurityConfig.class)` | Loading security config in sliced tests |
| `@MockitoBean JwtDecoder` | Mock JWT decoding |
| **@DataJpaTest** | |
| TestEntityManager | Persist and flush test entities |
| Custom queries | Test `@Query` methods |
| Native queries | Test native SQL |
| Pagination / Sorting | `PageRequest.of(0, 10, Sort.by("name"))` |
| Cascading relationships | OneToMany, ManyToMany cascades |
| **@WebFluxTest** | Reactive controller testing with `WebTestClient` |
| **@DataMongoTest** | MongoDB repository testing |
| **@JsonTest** | Serialization/deserialization with `JacksonTester` |

**What you'll test**: Controllers, repositories, JSON — each in isolation.

**Coverage contribution**: Controller + repository layers covered.

---

### Phase 4 — Service Layer Testing (100% Coverage Focus)

**Branch**: `phase-04-service-coverage`

| Topic | Description |
|-------|-------------|
| **Happy paths** | Expected inputs → expected outputs |
| **Exception paths** | |
| `when().thenThrow()` | Force exceptions from dependencies |
| Catch block verification | Assert fallback values, logging, rethrowing |
| Checked vs unchecked exceptions | Different handling strategies |
| Nested try-catch | Inner and outer catch blocks |
| Finally blocks | Verify cleanup always runs |
| **Null handling** | |
| Null inputs | What happens when args are null |
| `Optional.empty()` | Repository returns nothing |
| **Empty collections** | Empty lists, empty `Page<>` |
| **Boundary values** | 0, -1, `Integer.MAX_VALUE`, `""`, `" "` |
| **Conditional branches** | |
| Every `if`/`else` path | Both true and false |
| Switch cases + default | Every case covered |
| Ternary operators | Both outcomes |
| **Loop coverage** | 0, 1, N iterations |

**What you'll test**: Every line of every service method.

**Coverage contribution**: This is where you go from ~60% to ~95% coverage.

---

### Phase 5 — Testing Complex Scenarios

**Branch**: `phase-05-complex-scenarios`

| Topic | Description |
|-------|-------------|
| **Kafka** | |
| `@EmbeddedKafka` | In-memory Kafka broker for tests |
| Producer testing | Verify messages sent to correct topic |
| Consumer testing | Verify message processing logic |
| **REST clients** | |
| `MockRestServiceServer` | Mock outbound REST calls (RestTemplate) |
| WireMock | Standalone mock HTTP server |
| **WebClient mocking** | Mock reactive HTTP calls to external APIs |
| **Caching** | |
| `@EnableCaching` + `CacheManager` | Verify cache hits/misses |
| Cache eviction | Verify cache cleared on updates |
| **Async** | |
| `@Async` methods | Test with `CompletableFuture` |
| Awaiting async results | Using `Awaitility` or `.get()` |
| **Scheduled tasks** | |
| `@Scheduled` methods | Test the method logic directly |
| Trigger verification | Verify scheduling config |
| **File operations** | |
| `MockMultipartFile` | Upload testing |
| File download | Response content verification |
| **Transactions** | |
| Rollback testing | Verify rollback on failure |
| `@Transactional` in tests | Auto-rollback after each test |

**What you'll test**: Infrastructure integrations.

**Coverage contribution**: Covers async, messaging, caching, file I/O paths.

---

### Phase 6 — Exception & Error Path Coverage

**Branch**: `phase-06-exception-paths`

| Topic | Description |
|-------|-------------|
| `@ExceptionHandler` | Test each handler method returns correct status/body |
| `@ControllerAdvice` | Global exception handling tests |
| Custom exception classes | Verify message, status code, error fields |
| Error response DTOs | Serialization of error responses |
| `@NotNull` / `@Size` / `@Email` / `@Pattern` | Validation error messages |
| `ResponseStatusException` | Direct HTTP status exceptions |
| `HttpClientErrorException` | External service error handling |
| Database failures | Simulated `DataAccessException` |
| Connection timeouts | Simulated `ConnectException` |
| `JsonProcessingException` | Serialization/deserialization failures |
| Retry logic | Test retry count, backoff, exhaustion |
| Circuit breaker | Open, half-open, closed states |

**What you'll test**: Every error path in the application.

**Coverage contribution**: Those hard-to-reach catch blocks that JaCoCo highlights in red.

---

### Phase 7 — Achieving 100% Code Coverage (JaCoCo)

**Branch**: `phase-07-jacoco-coverage`

| Topic | Description |
|-------|-------------|
| JaCoCo Maven plugin setup | `jacoco-maven-plugin` configuration |
| Line coverage | Every line executed at least once |
| Branch coverage | Both `true`/`false` of every `if` |
| Method coverage | Every method called at least once |
| Reading JaCoCo reports | `target/site/jacoco/index.html` |
| Finding uncovered lines | Red = missed, Yellow = partial branch |
| Covering "unreachable" catch blocks | Mocking to force exceptions |
| Default switch cases | `@EnumSource` + unknown values |
| Lambda / Stream coverage | Each lambda body executed |
| `equals()` / `hashCode()` / `toString()` | Lombok-generated method testing |
| Excluding generated code | `lombok.config` + JaCoCo exclusions |
| Coverage thresholds | Fail build if coverage drops below X% |
| Coverage enforcement in CI | `check` goal with rules |

**What you'll test**: Gaps identified by JaCoCo reports.

**Coverage contribution**: The final push from 95% to 100%.

---

### Phase 8 — Best Practices & Patterns

**Branch**: `phase-08-best-practices`

| Topic | Description |
|-------|-------------|
| **AAA pattern** | Arrange → Act → Assert in every test |
| **One assertion concept** | Each test verifies one behavior |
| **Naming convention** | `methodName_scenario_expectedResult` |
| **Test data builders** | Builder pattern for test entities |
| **Factory methods** | `createValidUser()`, `createInvalidOrder()` |
| **`@TestConfiguration`** | Test-specific beans |
| **`@ActiveProfiles("test")`** | Test property overrides |
| **Test independence** | No shared mutable state, any execution order |
| **Mock vs real** | When to mock, when to use real implementations |
| **Test readability** | Tests as documentation |
| **Test speed** | Prefer unit over integration, minimize Spring context |

**What you'll learn**: Refactor all previous tests to follow best practices.

**Coverage contribution**: Same coverage, better maintainability.

---

### Phase 9 — Advanced Libraries & Tools

**Branch**: `phase-09-advanced-libs`

| Topic | Description |
|-------|-------------|
| **AssertJ** | `assertThat(list).hasSize(3).contains("a").doesNotContain("z")` |
| Fluent assertions | Chained, readable assertions |
| Custom assertions | `assertThat(user).hasValidEmail()` |
| Exception assertions | `assertThatThrownBy(() -> ...).isInstanceOf(...)` |
| **Testcontainers** | |
| PostgreSQL container | Real DB in Docker for tests |
| Kafka container | Real Kafka broker in Docker |
| Redis container | Real cache in Docker |
| `@Container` / `@Testcontainers` | JUnit 5 integration |
| **Awaitility** | |
| `await().atMost(5, SECONDS)` | Async assertion waiting |
| `until()` / `untilAsserted()` | Condition-based waiting |
| **ArchUnit** | |
| Layer dependency rules | "Controllers must not access repositories" |
| Naming conventions | "Services must end with Service" |
| Package rules | "No cycles between packages" |
| **PIT Mutation Testing** | |
| What is mutation testing | Validates test quality, not just coverage |
| `pitest-maven` plugin | Configuration and execution |
| Reading mutation reports | Killed vs survived mutants |
| Improving mutation score | Strengthen weak tests |

**Coverage contribution**: Same code coverage, but **proven** test quality.

---

### Phase 10 — Microservice-Specific Testing

**Branch**: `phase-10-microservice`

| Topic | Description |
|-------|-------------|
| **Spring Cloud Contract** | |
| Contract DSL | Define API contracts in Groovy/YAML |
| Producer verification | Auto-generated tests from contracts |
| Consumer stubs | Auto-generated WireMock stubs |
| **REST Assured** | |
| Fluent API testing | `given().when().get("/api").then().statusCode(200)` |
| Response body validation | JsonPath assertions |
| **Feign Client testing** | |
| `@FeignClient` mock | Test inter-service calls |
| Fallback testing | Circuit breaker fallbacks |
| **Configuration testing** | |
| `@TestPropertySource` | Override properties per test |
| `@ActiveProfiles` | Profile-specific behavior |
| `@DynamicPropertySource` | Testcontainers-injected properties |
| **Database migration testing** | |
| Flyway test migrations | Verify migrations apply cleanly |
| Rollback testing | Verify migrations are reversible |

**Coverage contribution**: Inter-service communication paths covered.

---

### Phase 11 — Security Testing Deep Dive

**Branch**: `phase-11-security`

| Topic | Description |
|-------|-------------|
| **Authentication** | |
| `.with(jwt())` | Mock JWT tokens in tests |
| Custom claims | `jwt().jwt(j -> j.claim("role", "ADMIN"))` |
| Token expiry | Expired token handling |
| Invalid signature | Tampered token handling |
| Missing token | 401 Unauthorized response |
| **Authorization** | |
| `@PreAuthorize` | Method-level security testing |
| Role-based access | ADMIN vs USER vs anonymous |
| Resource ownership | Users can only access their own data |
| **CORS** | |
| Allowed origins | Verify accepted origins |
| Blocked origins | Verify rejected origins |
| Preflight requests | OPTIONS handling |
| **CSRF** | |
| CSRF enabled endpoints | Token required for state-changing ops |
| CSRF disabled endpoints | API endpoints exempt |
| **Rate limiting** | |
| Under limit | Request succeeds |
| At limit | 429 Too Many Requests |
| After cooldown | Request succeeds again |

**Coverage contribution**: All security filter chain paths covered.

---

## Doc Format for Each Phase

Every `docs/phase-XX-name.md` will contain:

```
# Phase X — Title

## Overview
What this phase covers and why it matters.

## Prerequisites
What you should know from previous phases.

## Concepts

### Topic Name
**What it is**: Clear explanation.
**Why it matters**: Practical motivation.
**Code example**: Working code from this project.
**Common mistake**: What beginners get wrong.
**Coverage impact**: How this helps reach 100%.

## Exercises
Hands-on tasks to try yourself.

## Checklist
☐ I understand [concept]
☐ I can write [type of test]
☐ I ran all tests and they pass
```

---

## Quick Reference: Dependencies

```xml
<!-- Testing dependencies used across phases -->
spring-boot-starter-test     → JUnit 5, Mockito, AssertJ, MockMvc (Phase 1-8)
spring-security-test          → jwt(), SecurityMockMvcRequestPostProcessors (Phase 3+)
spring-kafka-test             → @EmbeddedKafka (Phase 5)
testcontainers                → Real Docker containers (Phase 9)
awaitility                    → Async testing (Phase 9)
archunit-junit5               → Architecture tests (Phase 9)
pitest-maven                  → Mutation testing (Phase 9)
spring-cloud-contract         → Contract tests (Phase 10)
rest-assured                  → API testing (Phase 10)
wiremock-standalone           → HTTP mock server (Phase 5+)
```

---

## How to Start

When ready to begin, use this prompt:

```
Build Phase 1 (Foundations) for the spring-boot-unit-testing project.
- Create branch: phase-01-foundations
- Write docs/phase-01-foundations.md with full explanations
- Write test classes demonstrating every JUnit 5 concept
- All tests must pass
- Refer to UNIT-TESTING-LEARNING-PLAN.md for the curriculum
```

Then for each subsequent phase:

```
Build Phase N ([name]) for the spring-boot-unit-testing project.
- Create branch: phase-NN-name from phase-(N-1) branch
- Write docs/phase-NN-name.md with full explanations
- Write test classes demonstrating every concept
- All previous tests must still pass
- Refer to UNIT-TESTING-LEARNING-PLAN.md for the curriculum
```
