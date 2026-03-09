# Unit Testing in Spring Boot — Complete Guide

---

## Table of Contents

1. [What is Unit Testing?](#1-what-is-unit-testing)
2. [Why is Unit Testing Required?](#2-why-is-unit-testing-required)
3. [What Happens If We Don't Write Tests?](#3-what-happens-if-we-dont-write-tests)
4. [The 4 Key Testing Annotations — Overview](#4-the-4-key-testing-annotations--overview)
5. [Arrange → Act → Assert (AAA) Pattern](#5-arrange--act--assert-aaa-pattern)
6. [@ExtendWith(MockitoExtension.class) — Deep Dive](#6-extendwithmockitoextensionclass--deep-dive)
7. [@WebMvcTest — Deep Dive](#7-webmvctest--deep-dive)
8. [@DataJpaTest — Deep Dive](#8-datajpatest--deep-dive)
9. [@SpringBootTest — Deep Dive](#9-springboottest--deep-dive)
10. [All Testing Annotations Explained](#10-all-testing-annotations-explained)
11. [Real-Time Examples — When to Use Which?](#11-real-time-examples--when-to-use-which)
12. [Combinations That DON'T Work](#12-combinations-that-dont-work)
13. [Combinations That SHOULD Be Used](#13-combinations-that-should-be-used)
14. [Best Case Studies](#14-best-case-studies)
15. [Worst Case Studies (Anti-Patterns)](#15-worst-case-studies-anti-patterns)
16. [Testing Pyramid](#16-testing-pyramid)
17. [Quick Reference Cheat Sheet](#17-quick-reference-cheat-sheet)

---

## 1. What is Unit Testing?

Unit testing is the practice of testing **individual units (methods/classes)** of your code **in isolation** to verify they work correctly.

### What is a "Unit"?

```
A "unit" = one method or one class

Example:
  ProductServiceImpl.createProduct()    ← this is ONE unit
  ProductServiceImpl.getProductById()   ← this is ANOTHER unit
  ProductController.createProduct()     ← this is ANOTHER unit
```

### Simple Analogy

Think of building a car:
- **Unit Test** = Testing each part individually (engine alone, brakes alone, lights alone)
- **Integration Test** = Testing parts working together (engine + transmission)
- **End-to-End Test** = Driving the full car on a road

You wouldn't assemble a car without testing each part first, right? Same with code.

### What Does a Unit Test Look Like?

```java
@Test
void shouldCalculateTotalPrice() {
    // Test ONE specific behavior
    BigDecimal price = new BigDecimal("100.00");
    int quantity = 3;

    BigDecimal total = orderService.calculateTotal(price, quantity);

    assertEquals(new BigDecimal("300.00"), total);  // Pass or Fail
}
```

---

## 2. Why is Unit Testing Required?

### 2.1 Catches Bugs Early

```
Without tests:
  Write Code → Deploy → User finds bug → Panic → Hotfix → Redeploy
  Cost: HIGH (production downtime, angry users)

With tests:
  Write Code → Run Tests → Test fails → Fix immediately → Deploy safely
  Cost: LOW (caught before production)
```

### 2.2 Confidence to Refactor

```
Scenario: You need to refactor ProductServiceImpl

Without tests:
  "Will my changes break something? I'm scared to touch this code."

With tests:
  Change code → Run tests → All green? → Ship it confidently.
  Something broke? → Test tells you EXACTLY what and where.
```

### 2.3 Documentation That Never Goes Stale

```java
// This test IS documentation — it tells you:
// "When product doesn't exist, the service should throw ResourceNotFoundException"
@Test
void getProductById_WhenNotExists_ShouldThrowException() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class,
            () -> productService.getProductById(99L));
}
```

### 2.4 Faster Development (Long Term)

```
Day 1-10:   Writing tests feels slow (you're learning)
Day 10-30:  About the same speed with/without tests
Day 30+:    WAY faster WITH tests (no manual testing, instant feedback)
Day 100+:   Without tests = nightmare (every change might break something)
```

### 2.5 Team Collaboration

```
Developer A writes ProductService + tests
Developer B modifies ProductService later
Developer B runs tests → sees what broke → knows exactly what A expected
```

---

## 3. What Happens If We Don't Write Tests?

### Real-World Disaster Scenarios

#### Scenario 1: The Silent Bug
```
ProductService.calculateDiscount() has a bug — gives 50% instead of 5%.
No tests → Bug goes to production → Company loses $100,000 in revenue.
With tests → Caught immediately during CI/CD pipeline.
```

#### Scenario 2: The Breaking Refactor
```
Developer changes ProductRepository.findByCategory() query.
No tests → Query now returns wrong data → Orders are placed for wrong products.
With tests → DataJpaTest catches the broken query instantly.
```

#### Scenario 3: The "It Works on My Machine"
```
Developer A: "Works on my machine!"
Developer B: "It's broken on mine!"
No tests → Hours of debugging, finger-pointing.
With tests → CI/CD runs tests on every push → catches environment issues.
```

#### Scenario 4: The Fear of Change
```
6 months later, nobody wants to touch the codebase.
"If I change this method, I don't know what else will break."
No tests → Codebase becomes frozen/legacy.
With tests → Change anything with confidence.
```

### Summary: Cost of NOT Testing

| Stage          | Without Tests          | With Tests              |
|----------------|------------------------|-------------------------|
| Development    | Slightly faster        | Slightly slower         |
| Debugging      | Hours/days             | Minutes (test tells you)|
| Refactoring    | Terrifying             | Confident               |
| Production bug | $$$$ expensive         | Caught before deploy    |
| Team onboard   | "What does this do?"   | Tests explain behavior  |
| 6 months later | Legacy nightmare       | Maintainable codebase   |

---

## 4. The 4 Key Testing Annotations — Overview

### Comparison Table

| Annotation | What it tests | What it loads | Speed | Spring Context? |
|---|---|---|---|---|
| `@ExtendWith(MockitoExtension.class)` | Pure unit tests (Service layer) | Nothing — just Mockito | ⚡ Fastest | ❌ No |
| `@WebMvcTest` | Controller layer only | Web layer (no DB, no service) | 🚀 Fast | ⚠️ Partial |
| `@DataJpaTest` | Repository layer only | JPA + H2 database | 🏃 Medium | ⚠️ Partial |
| `@SpringBootTest` | Integration tests (full app) | Entire Spring context | 🐢 Slowest | ✅ Full |

### What Can Each Test?

| Annotation | Can test what? | But BEST used for |
|---|---|---|
| `@ExtendWith` | Service, Controller, any Java class | **Service layer** (pure logic, no Spring needed) |
| `@WebMvcTest` | Controller only | **Controller layer** (HTTP endpoints, validation) |
| `@DataJpaTest` | Repository only | **Repository layer** (queries, DB operations) |
| `@SpringBootTest` | **Everything** | **Integration tests** (all layers working together) |

### Visual — Which Layer Gets Which Annotation

```
┌─────────────────────────────────────────────────────────┐
│                    HTTP Request                          │
│                        │                                │
│    ┌───────────────────▼───────────────────┐            │
│    │         Controller Layer               │            │
│    │    @WebMvcTest  ← tests this          │            │
│    └───────────────────┬───────────────────┘            │
│                        │                                │
│    ┌───────────────────▼───────────────────┐            │
│    │          Service Layer                 │            │
│    │    @ExtendWith   ← tests this         │            │
│    └───────────────────┬───────────────────┘            │
│                        │                                │
│    ┌───────────────────▼───────────────────┐            │
│    │         Repository Layer               │            │
│    │    @DataJpaTest  ← tests this         │            │
│    └───────────────────┬───────────────────┘            │
│                        │                                │
│    ┌───────────────────▼───────────────────┐            │
│    │           Database                     │            │
│    └───────────────────────────────────────┘            │
│                                                         │
│    @SpringBootTest  ← tests ALL of the above together   │
└─────────────────────────────────────────────────────────┘
```

---

## 5. Arrange → Act → Assert (AAA) Pattern

The **AAA pattern** is the standard way to structure EVERY unit test. Think of it as a recipe:

### The Pattern

```
ARRANGE  →  Set up the test data and mock behavior (the "given")
ACT      →  Call the method you're testing (the "when")
ASSERT   →  Verify the result is correct (the "then")
```

### Example 1: Simple

```java
@Test
void shouldAddTwoNumbers() {
    // ARRANGE — prepare inputs
    int a = 5;
    int b = 3;

    // ACT — call the method
    int result = calculator.add(a, b);

    // ASSERT — check the result
    assertEquals(8, result);
}
```

### Example 2: With Mockito

```java
@Test
void getProductById_ShouldReturnProduct() {
    // ARRANGE — set up mock behavior
    Product product = Product.builder().id(1L).name("iPhone").build();
    when(productRepository.findById(1L)).thenReturn(Optional.of(product));

    // ACT — call the real service method
    ProductResponse response = productService.getProductById(1L);

    // ASSERT — verify result
    assertNotNull(response);
    assertEquals("iPhone", response.getName());

    // VERIFY — (optional 4th step) check mock interactions
    verify(productRepository, times(1)).findById(1L);
}
```

### Example 3: Testing Exceptions

```java
@Test
void getProductById_WhenNotExists_ShouldThrowException() {
    // ARRANGE
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    // ACT & ASSERT — combined because we expect an exception
    ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> productService.getProductById(99L)  // ACT
    );

    // Additional ASSERT on the exception
    assertEquals("Product not found with id: 99", exception.getMessage());
}
```

### Example 4: Controller Test (MockMvc)

```java
@Test
void createProduct_ShouldReturn201() throws Exception {
    // ARRANGE
    when(productService.createProduct(any())).thenReturn(productResponse);

    // ACT & ASSERT — MockMvc chains them together
    mockMvc.perform(post("/api/products")                    // ACT
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())                  // ASSERT
            .andExpect(jsonPath("$.name").value("iPhone"));   // ASSERT
}
```

### Why AAA Matters

```
❌ BAD — No structure, hard to read:
@Test
void test1() {
    assertEquals("iPhone", productService.getProductById(1L).getName());
    when(repo.findById(1L)).thenReturn(Optional.of(new Product()));  // WRONG ORDER!
}

✅ GOOD — Clear AAA structure:
@Test
void getProductById_ShouldReturnProduct() {
    // Arrange
    when(repo.findById(1L)).thenReturn(Optional.of(product));

    // Act
    ProductResponse result = productService.getProductById(1L);

    // Assert
    assertEquals("iPhone", result.getName());
}
```

---

## 6. @ExtendWith(MockitoExtension.class) — Deep Dive

### What Is It?

`@ExtendWith` is a **JUnit 5** annotation that plugs in extensions. When you use `MockitoExtension.class`, it activates Mockito — a library that creates **fake objects** (mocks).

### Why Use It?

When testing `ProductServiceImpl`, you don't want to hit a real database. You want to test **only the service logic**. So you create a **fake repository** that returns whatever you tell it to.

### Key Annotations Inside

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock                              // Creates a FAKE ProductRepository
    private ProductRepository repo;    // This is NOT a real object — it's a puppet

    @InjectMocks                       // Creates REAL ProductServiceImpl
    private ProductServiceImpl service;// And injects the @Mock repo into it

    @Spy                               // Creates a REAL object but you can override specific methods
    private ObjectMapper mapper;       // Useful when you want mostly real behavior
}
```

### @Mock vs @InjectMocks vs @Spy

| Annotation | What it creates | When to use |
|---|---|---|
| `@Mock` | Fake object (all methods return null/0/false by default) | For dependencies you want to control |
| `@InjectMocks` | Real object with mocks injected into it | For the class you're actually testing |
| `@Spy` | Real object where you can override specific methods | When you need mostly real behavior |

### Mock Behavior — when/thenReturn

```java
// Tell the mock: "When findById(1L) is called, return this product"
when(productRepository.findById(1L)).thenReturn(Optional.of(product));

// Tell the mock: "When save() is called with ANY Product, return this product"
when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

// Tell the mock: "When delete() is called, do nothing" (void methods)
doNothing().when(productRepository).delete(any(Product.class));

// Tell the mock: "When findById(99L) is called, throw an exception"
when(productRepository.findById(99L)).thenThrow(new RuntimeException("DB Error"));
```

### Verify — Did the Mock Get Called?

```java
// Verify findById was called exactly once
verify(productRepository, times(1)).findById(1L);

// Verify save was called at least once
verify(productRepository, atLeastOnce()).save(any(Product.class));

// Verify delete was NEVER called
verify(productRepository, never()).delete(any(Product.class));

// Verify no more interactions with the mock
verifyNoMoreInteractions(productRepository);
```

### Argument Matchers

```java
// any() — matches any value
when(repo.save(any(Product.class))).thenReturn(product);

// eq() — matches exact value
when(repo.findById(eq(1L))).thenReturn(Optional.of(product));

// anyString(), anyLong(), anyInt() — type-specific matchers
when(repo.findByCategory(anyString())).thenReturn(List.of(product));

// ⚠️ RULE: If you use ANY matcher, ALL arguments must be matchers
// ❌ WRONG: when(service.update(1L, any())) — mixing literal and matcher
// ✅ RIGHT: when(service.update(eq(1L), any())) — all matchers
```

### ArgumentCaptor — Capture What Was Passed to Mock

```java
@Captor
private ArgumentCaptor<Product> productCaptor;

@Test
void createProduct_ShouldPassCorrectDataToRepository() {
    when(repo.save(any(Product.class))).thenReturn(product);

    productService.createProduct(request);

    // Capture what was actually passed to repo.save()
    verify(repo).save(productCaptor.capture());
    Product captured = productCaptor.getValue();

    assertEquals("iPhone 15", captured.getName());
    assertEquals(new BigDecimal("999.99"), captured.getPrice());
}
```

### When NOT to Use @ExtendWith

```
❌ Don't use for testing:
   - HTTP endpoints (use @WebMvcTest)
   - Database queries (use @DataJpaTest)
   - Multiple layers together (use @SpringBootTest)

✅ Use for testing:
   - Service layer business logic
   - Utility classes
   - Any plain Java class with dependencies
```

---

## 7. @WebMvcTest — Deep Dive

### What Is It?

`@WebMvcTest` loads **only the web layer** of your Spring application. It creates:
- Your controller class
- Exception handlers (@RestControllerAdvice)
- MockMvc (HTTP simulator)
- JSON serialization/deserialization

It does **NOT** load: services, repositories, or databases.

### Key Components

```java
@WebMvcTest(controllers = ProductController.class)  // Load ONLY this controller
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;            // HTTP simulator — no real server needed

    @MockBean                           // Creates mock AND registers in Spring context
    private ProductService service;     // ⚠️ @MockBean, NOT @Mock!

    @Autowired
    private ObjectMapper objectMapper;  // JSON converter (auto-provided by Spring)
}
```

### @MockBean vs @Mock — CRITICAL DIFFERENCE

```
@Mock      → Mockito creates a fake object. Spring knows NOTHING about it.
@MockBean  → Mockito creates a fake object AND Spring replaces the real bean with it.

In @WebMvcTest:
  - Spring creates the controller and tries to inject ProductService
  - @MockBean provides the fake ProductService to Spring
  - Without @MockBean, Spring would fail: "No bean of type ProductService found"

In @ExtendWith:
  - No Spring at all, so @MockBean is meaningless
  - Use @Mock + @InjectMocks instead
```

| Where | Use This | Why |
|---|---|---|
| `@ExtendWith` tests | `@Mock` | No Spring context exists |
| `@WebMvcTest` tests | `@MockBean` | Need to register mock in Spring context |
| `@SpringBootTest` tests | `@MockBean` (if mocking) | Need to replace real bean in Spring context |

### MockMvc — How It Works

```java
// MockMvc simulates HTTP without starting a real server

// GET request
mockMvc.perform(get("/api/products/{id}", 1L))
        .andExpect(status().isOk())                     // assert HTTP 200
        .andExpect(jsonPath("$.name").value("iPhone"));  // assert JSON body

// POST request with JSON body
mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());                // assert HTTP 201

// PUT request
mockMvc.perform(put("/api/products/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

// DELETE request
mockMvc.perform(delete("/api/products/{id}", 1L))
        .andExpect(status().isNoContent());              // assert HTTP 204

// GET with query parameters
mockMvc.perform(get("/api/products/search")
                .param("name", "iPhone")
                .param("page", "0"))
        .andExpect(status().isOk());
```

### JSON Path Assertions

```java
// Single field
.andExpect(jsonPath("$.name").value("iPhone"))

// Nested field
.andExpect(jsonPath("$.address.city").value("New York"))

// Array size
.andExpect(jsonPath("$", hasSize(3)))

// Array element
.andExpect(jsonPath("$[0].name").value("iPhone"))

// Field exists
.andExpect(jsonPath("$.errors.name").exists())

// Field does not exist
.andExpect(jsonPath("$.errors").doesNotExist())

// String contains
.andExpect(jsonPath("$.name", containsString("Phone")))
```

### What @WebMvcTest Tests

```
✅ Tests these:                    ❌ Does NOT test these:
   - URL mapping correct?            - Business logic
   - HTTP method correct?            - Database queries
   - Request body deserialized?      - Service implementations
   - Validation works?               - Repository methods
   - Response status correct?        - Entity relationships
   - Response body correct?
   - Exception handling works?
   - Path variables parsed?
   - Query params parsed?
```

---

## 8. @DataJpaTest — Deep Dive

### What Is It?

`@DataJpaTest` loads **only the JPA layer**:
- Entity classes (@Entity)
- Repository interfaces (JpaRepository)
- An embedded database (H2 by default)
- Hibernate and JPA configuration

### Key Behavior

```
1. AUTO-CONFIGURES H2 database (in-memory, fast)
2. Each test runs in a TRANSACTION
3. Transaction is ROLLED BACK after each test
4. So test data does NOT leak between tests
5. Only scans @Entity and @Repository — NOT @Service or @Controller
```

### Key Components

```java
@DataJpaTest
@ActiveProfiles("test")                    // Use test profile (H2 config)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repo;         // Real repository — hitting real H2 DB

    @Autowired
    private TestEntityManager entityManager; // JPA helper for test setup
}
```

### TestEntityManager — Why Use It?

```java
// TestEntityManager is a wrapper around JPA EntityManager for tests

// WHY use it instead of the repository for setup?
// Because you want to test the REPOSITORY, not use it to set up its own test data.

// Setup with TestEntityManager (GOOD — independent of what you're testing)
Product product = entityManager.persistAndFlush(Product.builder()
        .name("iPhone").price(new BigDecimal("999.99")).build());

// Then test the repository method
Optional<Product> found = productRepository.findById(product.getId());
assertTrue(found.isPresent());

// Setup with repository (OK but less clean — using repo to test repo)
Product saved = productRepository.save(product);
```

### TestEntityManager Methods

```java
// persist — save entity to DB
Product saved = entityManager.persist(product);

// persistAndFlush — save AND immediately write to DB
Product saved = entityManager.persistAndFlush(product);

// find — get entity by ID
Product found = entityManager.find(Product.class, 1L);

// flush — force pending changes to be written to DB
entityManager.flush();

// clear — clear persistence context (detach all entities)
entityManager.clear();

// detach — detach specific entity
entityManager.detach(product);
```

### What @DataJpaTest Tests

```
✅ Tests these:                       ❌ Does NOT test these:
   - Custom query methods work?          - Controllers
   - JPA mappings correct?               - Services
   - Entity relationships work?          - HTTP endpoints
   - @PrePersist/@PreUpdate work?        - Business logic
   - Constraints work (unique, etc)?     - Validation annotations
   - Named queries work?
   - Pagination works?
```

### Common Gotcha — @ActiveProfiles("test")

```
Without @ActiveProfiles("test"):
  → Spring tries to connect to MySQL (from application.yml)
  → Test FAILS because MySQL isn't running

With @ActiveProfiles("test"):
  → Spring uses application-test.yml
  → Connects to H2 in-memory database
  → Test PASSES without any external DB
```

---

## 9. @SpringBootTest — Deep Dive

### What Is It?

`@SpringBootTest` loads the **complete Spring application** — every bean, every configuration, everything. It's the closest thing to running the real app.

### Key Configurations

```java
// Default — loads full context, no web server
@SpringBootTest
class MyTest { }

// With MockMvc (HTTP simulator, no real server)
@SpringBootTest
@AutoConfigureMockMvc
class MyTest {
    @Autowired MockMvc mockMvc;
}

// With real web server on random port
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyTest {
    @Autowired TestRestTemplate restTemplate;  // for real HTTP calls
}

// With specific properties
@SpringBootTest(properties = {"spring.jpa.show-sql=true"})
class MyTest { }
```

### WebEnvironment Options

| Option | What it does | When to use |
|---|---|---|
| `MOCK` (default) | No real server, uses MockMvc | Most integration tests |
| `RANDOM_PORT` | Starts real server on random port | Testing real HTTP calls |
| `DEFINED_PORT` | Starts real server on configured port | Rarely used |
| `NONE` | No web environment at all | Service-only tests |

### Important — @SpringBootTest Does NOT Auto-Rollback

```
⚠️ Unlike @DataJpaTest, @SpringBootTest does NOT rollback transactions!

@DataJpaTest → Each test auto-rolls back → Data is cleaned automatically
@SpringBootTest → Each test COMMITS → You must clean up manually!

Solution:
@AfterEach
void tearDown() {
    productRepository.deleteAll();  // Manual cleanup
}

Or use @Transactional on the test class (but be careful — it can hide bugs)
```

### When to Use @SpringBootTest

```
✅ USE for:
   - End-to-end flow tests (Create → Read → Update → Delete)
   - Testing multiple layers working together
   - Testing configuration and bean wiring
   - Testing with real service + real repository + real DB

❌ DON'T USE for:
   - Testing a single method in isolation (use @ExtendWith)
   - Testing just the controller (use @WebMvcTest — faster)
   - Testing just the repository (use @DataJpaTest — faster)
   - Every single test (too slow!)
```

---

## 10. All Testing Annotations Explained

### JUnit 5 Core Annotations

| Annotation | Purpose | Example |
|---|---|---|
| `@Test` | Marks a method as a test | `@Test void shouldWork() {}` |
| `@DisplayName` | Custom test name in reports | `@DisplayName("Should create product")` |
| `@BeforeEach` | Runs BEFORE each test method | Setup test data |
| `@AfterEach` | Runs AFTER each test method | Cleanup (DB, files) |
| `@BeforeAll` | Runs ONCE before all tests (static) | Expensive setup (DB connection) |
| `@AfterAll` | Runs ONCE after all tests (static) | Expensive cleanup |
| `@Disabled` | Skips the test | `@Disabled("TODO: fix later")` |
| `@Nested` | Groups related tests in inner class | Organize by scenario |
| `@ParameterizedTest` | Run same test with different inputs | Test multiple values |
| `@RepeatedTest` | Run test multiple times | Flaky test detection |
| `@Tag` | Tag tests for filtering | `@Tag("slow")` |
| `@Timeout` | Fail if test takes too long | `@Timeout(5)` — 5 seconds |

### JUnit 5 — Lifecycle Example

```java
class ProductServiceTest {

    @BeforeAll
    static void beforeAll() {
        // Runs ONCE before all tests — e.g., start test container
        System.out.println("1. BeforeAll");
    }

    @BeforeEach
    void beforeEach() {
        // Runs before EACH test — e.g., create test data
        System.out.println("2. BeforeEach");
    }

    @Test
    void testA() {
        System.out.println("3. Test A");
    }

    @Test
    void testB() {
        System.out.println("3. Test B");
    }

    @AfterEach
    void afterEach() {
        // Runs after EACH test — e.g., cleanup
        System.out.println("4. AfterEach");
    }

    @AfterAll
    static void afterAll() {
        // Runs ONCE after all tests — e.g., stop test container
        System.out.println("5. AfterAll");
    }
}

// Output:
// 1. BeforeAll
// 2. BeforeEach  →  3. Test A  →  4. AfterEach
// 2. BeforeEach  →  3. Test B  →  4. AfterEach
// 5. AfterAll
```

### Mockito Annotations

| Annotation | Purpose | Used With |
|---|---|---|
| `@Mock` | Creates fake object | `@ExtendWith(MockitoExtension.class)` |
| `@InjectMocks` | Creates real object + injects mocks | `@ExtendWith(MockitoExtension.class)` |
| `@Spy` | Creates real object, override specific methods | `@ExtendWith(MockitoExtension.class)` |
| `@Captor` | Captures arguments passed to mocks | `@ExtendWith(MockitoExtension.class)` |
| `@MockBean` | Creates mock AND registers in Spring | `@WebMvcTest`, `@SpringBootTest` |
| `@SpyBean` | Creates spy AND registers in Spring | `@WebMvcTest`, `@SpringBootTest` |

### Spring Test Annotations

| Annotation | Purpose |
|---|---|
| `@ActiveProfiles("test")` | Activates a Spring profile (loads application-test.yml) |
| `@AutoConfigureMockMvc` | Adds MockMvc bean to `@SpringBootTest` |
| `@Transactional` | Wraps test in transaction (auto-rollback in test) |
| `@Sql` | Run SQL script before/after test |
| `@DirtiesContext` | Recreate Spring context after test (expensive!) |
| `@TestPropertySource` | Override properties for test |
| `@WithMockUser` | Simulate authenticated user (Spring Security) |

### Assertion Methods (JUnit 5)

```java
// Equality
assertEquals(expected, actual);
assertNotEquals(unexpected, actual);

// Null checks
assertNull(value);
assertNotNull(value);

// Boolean
assertTrue(condition);
assertFalse(condition);

// Same reference
assertSame(expected, actual);       // same object reference
assertNotSame(unexpected, actual);

// Exceptions
assertThrows(Exception.class, () -> method());
assertDoesNotThrow(() -> method());

// Collections
assertEquals(3, list.size());
assertTrue(list.isEmpty());
assertTrue(list.contains("item"));

// Multiple assertions (all run, even if one fails)
assertAll(
    () -> assertEquals("iPhone", response.getName()),
    () -> assertEquals(999.99, response.getPrice()),
    () -> assertNotNull(response.getId())
);

// Timeout
assertTimeout(Duration.ofSeconds(2), () -> slowMethod());
```

### @Nested — Grouping Tests

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Nested
    @DisplayName("Create Product")
    class CreateProduct {

        @Test
        @DisplayName("should create successfully")
        void shouldCreateSuccessfully() { /* ... */ }

        @Test
        @DisplayName("should fail when name is null")
        void shouldFailWhenNameIsNull() { /* ... */ }
    }

    @Nested
    @DisplayName("Get Product By ID")
    class GetProductById {

        @Test
        @DisplayName("should return product when exists")
        void shouldReturnWhenExists() { /* ... */ }

        @Test
        @DisplayName("should throw when not exists")
        void shouldThrowWhenNotExists() { /* ... */ }
    }
}

// Test report shows:
// ProductServiceTest
//   ├── Create Product
//   │   ├── ✅ should create successfully
//   │   └── ✅ should fail when name is null
//   └── Get Product By ID
//       ├── ✅ should return product when exists
//       └── ✅ should throw when not exists
```

### @ParameterizedTest — Multiple Inputs

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "  "})
@DisplayName("Should reject blank product names")
void shouldRejectBlankNames(String name) {
    ProductRequest request = ProductRequest.builder().name(name).build();
    // test validation...
}

@ParameterizedTest
@CsvSource({
    "100.00, 2, 200.00",
    "50.00, 3, 150.00",
    "0.99, 10, 9.90"
})
@DisplayName("Should calculate total correctly")
void shouldCalculateTotal(BigDecimal price, int qty, BigDecimal expected) {
    assertEquals(expected, service.calculateTotal(price, qty));
}
```

---

## 11. Real-Time Examples — When to Use Which?

### Scenario 1: "I wrote a new method in ProductServiceImpl that calculates discount"

```
→ Use: @ExtendWith(MockitoExtension.class)
→ Why: You're testing pure business logic. No need for Spring, DB, or HTTP.
→ Mock: ProductRepository (the dependency)
→ Test: The discount calculation logic
```

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository repo;
    @InjectMocks private ProductServiceImpl service;

    @Test
    void shouldApply10PercentDiscount() {
        Product product = Product.builder().price(new BigDecimal("100.00")).build();
        when(repo.findById(1L)).thenReturn(Optional.of(product));

        BigDecimal discounted = service.getDiscountedPrice(1L, 10);

        assertEquals(new BigDecimal("90.00"), discounted);
    }
}
```

### Scenario 2: "I added a new POST endpoint /api/products/bulk"

```
→ Use: @WebMvcTest
→ Why: You need to test the HTTP layer — URL mapping, request body, status codes.
→ Mock: ProductService (the dependency)
→ Test: Endpoint works, validation works, response format correct
```

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProductService service;

    @Test
    void bulkCreate_ShouldReturn201() throws Exception {
        mockMvc.perform(post("/api/products/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{...}, {...}]"))
                .andExpect(status().isCreated());
    }
}
```

### Scenario 3: "I wrote a custom query to find products by price range and category"

```
→ Use: @DataJpaTest
→ Why: You need to test the SQL query against a real database.
→ Mock: Nothing — use real H2 database
→ Test: Query returns correct results
```

```java
@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {
    @Autowired private ProductRepository repo;
    @Autowired private TestEntityManager em;

    @Test
    void shouldFindByCategoryAndPriceRange() {
        em.persistAndFlush(Product.builder()
                .name("iPhone").category("Electronics")
                .price(new BigDecimal("999.99")).quantity(10).build());

        List<Product> results = repo.findByCategoryAndPriceBetween(
                "Electronics", new BigDecimal("500"), new BigDecimal("1500"));

        assertEquals(1, results.size());
    }
}
```

### Scenario 4: "I need to test the complete order flow: create product → place order → deduct inventory"

```
→ Use: @SpringBootTest
→ Why: Multiple layers and services must work TOGETHER.
→ Mock: Nothing — use real everything
→ Test: The entire business flow works end-to-end
```

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepo;
    @Autowired private OrderRepository orderRepo;

    @Test
    void fullOrderFlow() throws Exception {
        // Create product
        mockMvc.perform(post("/api/products").content("..."))
                .andExpect(status().isCreated());

        // Place order
        mockMvc.perform(post("/api/orders").content("..."))
                .andExpect(status().isCreated());

        // Verify inventory deducted
        Product product = productRepo.findById(1L).orElseThrow();
        assertEquals(49, product.getQuantity());  // was 50, ordered 1
    }
}
```

### Quick Decision Tree

```
What are you testing?
│
├── A single method's LOGIC? (no HTTP, no DB)
│   └── ➡️ @ExtendWith(MockitoExtension.class)
│
├── An HTTP ENDPOINT? (URL, status code, JSON, validation)
│   └── ➡️ @WebMvcTest
│
├── A DATABASE QUERY? (custom query, JPA mapping)
│   └── ➡️ @DataJpaTest
│
└── MULTIPLE LAYERS together? (full flow, end-to-end)
    └── ➡️ @SpringBootTest
```

---

## 12. Combinations That DON'T Work

### ❌ @WebMvcTest + @DataJpaTest

```java
// ❌ COMPILE ERROR — they conflict!
@WebMvcTest(ProductController.class)
@DataJpaTest
class MyTest { }

// WHY: @WebMvcTest loads ONLY web layer. @DataJpaTest loads ONLY JPA layer.
//      They have contradicting configurations.
// FIX: Use @SpringBootTest if you need both web + JPA.
```

### ❌ @WebMvcTest + @SpringBootTest

```java
// ❌ CONFLICT — don't combine them!
@WebMvcTest
@SpringBootTest
class MyTest { }

// WHY: @WebMvcTest loads partial context. @SpringBootTest loads full context.
//      Spring doesn't know which configuration to use.
// FIX: Use @SpringBootTest + @AutoConfigureMockMvc for full context with MockMvc.
```

### ❌ @DataJpaTest + @SpringBootTest

```java
// ❌ CONFLICT
@DataJpaTest
@SpringBootTest
class MyTest { }

// WHY: Same reason — partial vs full context conflict.
// FIX: Use @SpringBootTest alone — it already includes JPA.
```

### ❌ @Mock inside @WebMvcTest (for Spring beans)

```java
@WebMvcTest(ProductController.class)
class MyTest {
    @Mock  // ❌ WRONG — Spring doesn't know about this mock
    private ProductService service;
}

// WHY: @Mock creates a Mockito mock but doesn't register it in Spring context.
//      The controller can't find a ProductService bean → test fails.
// FIX: Use @MockBean instead.
```

### ❌ @MockBean inside @ExtendWith

```java
@ExtendWith(MockitoExtension.class)
class MyTest {
    @MockBean  // ❌ WRONG — no Spring context exists
    private ProductRepository repo;
}

// WHY: @MockBean needs a Spring context to register the mock bean.
//      @ExtendWith doesn't create a Spring context.
// FIX: Use @Mock instead.
```

### ❌ @InjectMocks inside @WebMvcTest or @SpringBootTest

```java
@WebMvcTest(ProductController.class)
class MyTest {
    @InjectMocks  // ❌ WRONG — Spring manages the controller, not Mockito
    private ProductController controller;
}

// WHY: In Spring tests, Spring creates and wires beans.
//      @InjectMocks is Mockito's way of wiring — they conflict.
// FIX: Use @Autowired for Spring-managed beans.
```

### Summary Table — What Goes With What

| | `@Mock` | `@MockBean` | `@InjectMocks` | `@Autowired` |
|---|---|---|---|---|
| `@ExtendWith` | ✅ Yes | ❌ No | ✅ Yes | ❌ No |
| `@WebMvcTest` | ❌ No* | ✅ Yes | ❌ No | ✅ Yes |
| `@DataJpaTest` | ❌ No* | ✅ Yes (rare) | ❌ No | ✅ Yes |
| `@SpringBootTest` | ❌ No* | ✅ Yes | ❌ No | ✅ Yes |

> *`@Mock` can technically exist in any test, but it won't be injected into Spring-managed beans. Only use it in `@ExtendWith` tests.

---

## 13. Combinations That SHOULD Be Used

### ✅ @ExtendWith + @Mock + @InjectMocks

```java
// The classic combo for service layer tests
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository repo;
    @InjectMocks private ProductServiceImpl service;
}
```

### ✅ @WebMvcTest + @MockBean + @Autowired MockMvc

```java
// The classic combo for controller tests
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProductService service;
}
```

### ✅ @DataJpaTest + @ActiveProfiles("test") + @Autowired

```java
// The classic combo for repository tests
@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {
    @Autowired private ProductRepository repo;
    @Autowired private TestEntityManager em;
}
```

### ✅ @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")

```java
// The classic combo for integration tests
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository repo;
}
```

### ✅ @SpringBootTest + @MockBean (partial mocking)

```java
// When you want full context but mock ONE specific dependency (e.g., external API)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private PaymentGateway paymentGateway;  // Mock only the external API

    @Test
    void placeOrder_ShouldCallPaymentGateway() {
        when(paymentGateway.charge(any())).thenReturn(PaymentResult.SUCCESS);
        // ... test with real service + repo but fake payment gateway
    }
}
```

---

## 14. Best Case Studies

### Best Practice 1: Test Naming Convention

```java
// Pattern: methodName_scenario_expectedBehavior

✅ GOOD:
getProductById_WhenProductExists_ShouldReturnProduct()
getProductById_WhenProductNotExists_ShouldThrowResourceNotFoundException()
createProduct_WithValidRequest_ShouldReturnCreatedProduct()
createProduct_WithBlankName_ShouldThrowValidationException()

❌ BAD:
test1()
testGetProduct()
itWorks()
```

### Best Practice 2: One Assertion Per Concept

```java
// Test ONE behavior per test method

✅ GOOD:
@Test void createProduct_ShouldReturnCorrectName() {
    ProductResponse response = service.createProduct(request);
    assertEquals("iPhone", response.getName());
}

@Test void createProduct_ShouldCallRepositorySave() {
    service.createProduct(request);
    verify(repo, times(1)).save(any());
}

❌ BAD (but acceptable for related assertions):
@Test void createProduct_ShouldWork() {
    ProductResponse response = service.createProduct(request);
    assertEquals("iPhone", response.getName());
    assertEquals(999.99, response.getPrice());
    verify(repo).save(any());
    verify(repo, never()).delete(any());
    // ... 20 more assertions
}

// ✅ BETTER — use assertAll for related field checks:
@Test void createProduct_ShouldReturnCorrectProduct() {
    ProductResponse response = service.createProduct(request);
    assertAll(
        () -> assertEquals("iPhone", response.getName()),
        () -> assertEquals(999.99, response.getPrice()),
        () -> assertEquals("Electronics", response.getCategory())
    );
}
```

### Best Practice 3: Test the Right Layer

```java
// Test validation in @WebMvcTest, NOT in @ExtendWith

// ❌ WRONG — testing validation in service test
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Test void shouldRejectBlankName() {
        // Validation happens in controller (@Valid), not in service!
        // This test doesn't even trigger validation.
    }
}

// ✅ RIGHT — testing validation in controller test
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Test void shouldReturn400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/products")
                .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

### Best Practice 4: Use @BeforeEach for Common Setup

```java
// ✅ GOOD — shared setup, clean tests
@BeforeEach
void setUp() {
    product = Product.builder().id(1L).name("iPhone").build();
    request = ProductRequest.builder().name("iPhone").build();
}

@Test void test1() {
    when(repo.findById(1L)).thenReturn(Optional.of(product));
    // ... clean, focused test
}

// ❌ BAD — duplicated setup in every test
@Test void test1() {
    Product product = Product.builder().id(1L).name("iPhone").build();
    when(repo.findById(1L)).thenReturn(Optional.of(product));
    // ...
}
@Test void test2() {
    Product product = Product.builder().id(1L).name("iPhone").build();  // duplicate!
    when(repo.findById(1L)).thenReturn(Optional.of(product));
    // ...
}
```

### Best Practice 5: Test Both Happy and Unhappy Paths

```java
// For every method, test:
// 1. Happy path — normal expected behavior
// 2. Edge cases — boundary values
// 3. Error cases — what should fail

// Happy path
@Test void getProductById_WhenExists_ShouldReturnProduct() { }

// Error path
@Test void getProductById_WhenNotExists_ShouldThrowException() { }

// Edge case
@Test void getAllProducts_WhenEmpty_ShouldReturnEmptyList() { }
```

---

## 15. Worst Case Studies (Anti-Patterns)

### Anti-Pattern 1: Testing Implementation Instead of Behavior

```java
// ❌ BAD — tests HOW it works (brittle, breaks on refactor)
@Test void createProduct() {
    service.createProduct(request);
    verify(repo).save(any());                    // testing implementation detail
    verify(mapper).toEntity(any());              // testing implementation detail
    verify(mapper).toResponse(any());            // testing implementation detail
    verifyNoMoreInteractions(repo, mapper);      // over-verification
}

// ✅ GOOD — tests WHAT it does (stable, survives refactors)
@Test void createProduct_ShouldReturnProductWithCorrectName() {
    when(repo.save(any())).thenReturn(product);

    ProductResponse response = service.createProduct(request);

    assertEquals("iPhone", response.getName());
}
```

### Anti-Pattern 2: Using @SpringBootTest for Everything

```java
// ❌ BAD — slow tests, 60+ seconds for full suite
@SpringBootTest  // loads ENTIRE app just to test one service method
class ProductServiceTest {
    @Test void shouldCreateProduct() { }  // could be done with @ExtendWith in 0.1s
}

// ✅ GOOD — use the lightest annotation possible
@ExtendWith(MockitoExtension.class)  // 0.1 seconds
class ProductServiceTest {
    @Test void shouldCreateProduct() { }
}
```

### Anti-Pattern 3: No Assertions

```java
// ❌ BAD — test runs but proves nothing
@Test void createProduct() {
    service.createProduct(request);  // no assertion! What are we testing?
}

// ✅ GOOD — always assert something meaningful
@Test void createProduct_ShouldReturnProductWithId() {
    when(repo.save(any())).thenReturn(product);

    ProductResponse response = service.createProduct(request);

    assertNotNull(response.getId());
}
```

### Anti-Pattern 4: Tests That Depend on Each Other

```java
// ❌ BAD — test2 depends on test1 running first
@Test void test1_createProduct() {
    service.createProduct(request);  // saves to shared state
}

@Test void test2_getProduct() {
    service.getProductById(1L);  // assumes test1 ran first!
}

// ✅ GOOD — each test is independent
@Test void getProduct_ShouldReturnProduct() {
    // ARRANGE — set up its own data
    when(repo.findById(1L)).thenReturn(Optional.of(product));

    // ACT
    ProductResponse response = service.getProductById(1L);

    // ASSERT
    assertNotNull(response);
}
```

### Anti-Pattern 5: Mocking Everything (Even What You're Testing)

```java
// ❌ BAD — you're testing nothing real
@Test void createProduct() {
    when(service.createProduct(any())).thenReturn(response);  // mocking the thing you're testing!

    ProductResponse result = service.createProduct(request);

    assertEquals(response, result);  // of course it equals — you just told it to return that!
}

// ✅ GOOD — mock dependencies, test the real class
@Test void createProduct() {
    when(repo.save(any())).thenReturn(product);  // mock the DEPENDENCY

    ProductResponse result = service.createProduct(request);  // call the REAL method

    assertEquals("iPhone", result.getName());
}
```

### Anti-Pattern 6: Catching Exceptions Instead of Using assertThrows

```java
// ❌ BAD — verbose and can silently pass
@Test void shouldThrowException() {
    try {
        service.getProductById(99L);
        fail("Should have thrown exception");  // easy to forget this line
    } catch (ResourceNotFoundException e) {
        assertEquals("Product not found", e.getMessage());
    }
}

// ✅ GOOD — clean, clear, impossible to miss
@Test void shouldThrowException() {
    assertThrows(ResourceNotFoundException.class,
            () -> service.getProductById(99L));
}
```

---

## 16. Testing Pyramid

```
                    /\
                   /  \
                  / E2E \          ← Few (slow, expensive)
                 /  Tests \           @SpringBootTest
                /──────────\
               / Integration \     ← Some (medium speed)
              /    Tests      \       @WebMvcTest, @DataJpaTest
             /────────────────\
            /    Unit Tests     \  ← Many (fast, cheap)
           /  @ExtendWith(Mock)  \
          /──────────────────────\

Recommended Ratio:
  70% → Unit Tests (@ExtendWith)        — fast, test logic
  20% → Slice Tests (@WebMvcTest, etc.) — medium, test layers
  10% → Integration Tests (@SpringBoot) — slow, test full flow
```

### Why This Pyramid?

```
Unit Tests (70%):
  - Run in milliseconds
  - Test every edge case of business logic
  - Easy to write and maintain
  - No external dependencies

Slice Tests (20%):
  - Run in seconds
  - Test HTTP endpoints and DB queries
  - Catch integration issues within a layer

Integration Tests (10%):
  - Run in seconds to minutes
  - Test critical business flows end-to-end
  - Catch issues between layers
  - Expensive to write and maintain
```

---

## 17. Quick Reference Cheat Sheet

### Which Annotation For What?

```
Testing ProductServiceImpl.createProduct()  → @ExtendWith
Testing POST /api/products returns 201      → @WebMvcTest
Testing repo.findByCategory() query         → @DataJpaTest
Testing full create-to-read flow            → @SpringBootTest
```

### Which Injection For Where?

```
@ExtendWith  → @Mock + @InjectMocks
@WebMvcTest  → @MockBean + @Autowired
@DataJpaTest → @Autowired (no mocks needed — use real H2)
@SpringBootTest → @Autowired (+ @MockBean for external deps)
```

### Test File Location

```
src/test/java/com/ecommerce/unittesting/
├── extendwith/        → Pure unit tests
├── webmvctest/        → Controller tests
├── datajpatest/       → Repository tests
└── springboottest/    → Integration tests
```

### Running Tests

```bash
# Run ALL tests
mvn test

# Run specific test class
mvn test -Dtest=ProductServiceTest

# Run specific test method
mvn test -Dtest=ProductServiceTest#createProduct_ShouldReturnSavedProduct

# Run tests in a specific package
mvn test -Dtest="com.ecommerce.unittesting.extendwith.*"
```

---

> **Remember:** The goal of testing is NOT 100% code coverage. The goal is **confidence** that your code works correctly and **safety** to change it without fear.
