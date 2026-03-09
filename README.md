# Spring Boot Unit Testing

A hands-on Spring Boot project for learning unit testing from scratch using an e-commerce domain (Products & Users).

## Tech Stack

- **Java 21** / **Spring Boot 3.2.2**
- **Spring Web** — REST APIs
- **Spring Data JPA** — Database layer
- **MySQL 8.0** — Runtime database (via Docker)
- **H2** — In-memory database for tests
- **Lombok** — Boilerplate reduction
- **JUnit 5 + Mockito** — Testing framework

## Project Structure

```
src/
├── main/java/com/ecommerce/unittesting/
│   ├── controller/        # REST endpoints (ProductController, UserController)
│   ├── dto/               # Request/Response DTOs + UserRef
│   ├── entity/            # JPA entities (Product, User)
│   ├── exception/         # Custom exceptions + GlobalExceptionHandler
│   ├── repository/        # Spring Data JPA repositories
│   └── service/           # Service interfaces + implementations
└── test/java/com/ecommerce/unittesting/
    ├── extendwith/        # @ExtendWith(MockitoExtension) — pure unit tests
    ├── webmvctest/        # @WebMvcTest — controller layer tests with MockMvc
    ├── datajpatest/       # @DataJpaTest — repository tests with H2
    └── springboottest/    # @SpringBootTest — full integration tests
```

## Domain Model

- **User** — firstName, lastName, email (unique), phone, role
- **Product** — name, description, price, quantity, category, createdBy (User), updatedBy (User)
- **UserRef** — lightweight DTO with id + username for Product responses

## Testing Strategy

| Annotation | What it tests | Context loaded | Database |
|------------|--------------|----------------|----------|
| `@ExtendWith(MockitoExtension.class)` | Service logic | None (pure Mockito) | None |
| `@WebMvcTest` | Controller + HTTP layer | Web layer only | None |
| `@DataJpaTest` | Repository + JPA queries | JPA layer only | H2 in-memory |
| `@SpringBootTest` | Full application flow | Full Spring context | H2 in-memory |

### Test Classes (8 total)

- `ProductServiceTest` — 2 mocks (ProductRepo + UserRepo), tests user-not-found prevents product save
- `UserServiceTest` — 1 mock, tests duplicate email prevention
- `ProductControllerTest` — MockMvc, tests UserRef in JSON response, validation
- `UserControllerTest` — MockMvc, tests 409 conflict on duplicate email
- `ProductRepositoryTest` — Persists User first (FK), tests relationship mapping
- `UserRepositoryTest` — Tests unique constraint, existsByEmail
- `ProductIntegrationTest` — Creates real User then Product, tests update with different updatedBy
- `UserIntegrationTest` — Full CRUD flow, duplicate email prevention

## Getting Started

### Prerequisites

- Java 21
- Maven
- Docker & Docker Compose

### Run MySQL

```bash
docker-compose up -d
```

### Run the Application

```bash
mvn spring-boot:run
```

App starts on `http://localhost:8090`

### Run Tests

```bash
mvn test
```

## API Endpoints

### Users — `/api/users`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users` | Create user |
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get user by ID |
| GET | `/api/users/email/{email}` | Get user by email |
| GET | `/api/users/role/{role}` | Get users by role |
| GET | `/api/users/search?firstName=` | Search by first name |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |

### Products — `/api/products`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products` | Get all products |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products/category/{category}` | Get by category |
| GET | `/api/products/search?name=` | Search by name |
| GET | `/api/products/price-range?min=&max=` | Filter by price range |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |

### Error Responses

| Status | Scenario |
|--------|----------|
| 400 | Validation failed (blank fields, invalid email, negative price) |
| 404 | Resource not found (invalid ID) |
| 409 | Duplicate email on user creation |

## Learning Docs

- [Unit-Testing-Guide.md](learning-docs/Unit-Testing-Guide.md) — Comprehensive guide covering all 4 testing annotations, AAA pattern, best practices, testing pyramid
- [Unit-Testing-API-Testing.md](learning-docs/Unit-Testing-API-Testing.md) — Sample payloads and endpoints for manual API testing
