# 🎯 Resource Booking System

A secure, RESTful **Resource Booking System** built with **Spring Boot**, featuring JWT authentication, role-based access control, reservation management, double-booking prevention, dynamic filtering, pagination, and sorting.

---

## 📋 Table of Contents

* [Tech Stack](#-tech-stack)
* [Features](#-features)
* [Project Structure](#-project-structure)
* [Setup Instructions](#-setup-instructions)
* [Database Configuration](#-database-configuration)
* [Environment Variables](#-environment-variables)
* [Seed Users](#-seed-users-test-credentials)
* [API Documentation](#-api-documentation)
* [Security Design](#-security-design)
* [Running Tests](#-running-tests)
* [Example Requests](#-example-requests)
* [Quick Start](#-quick-start)

---

## 🛠 Tech Stack

| Component         | Technology                          |
| ----------------- | ----------------------------------- |
| Language          | Java 17                             |
| Framework         | Spring Boot 3.5.0                   |
| Security          | Spring Security 6 + JWT (JJWT 0.12) |
| Persistence       | Spring Data JPA / Hibernate         |
| Database          | MySQL 8.x                           |
| Validation        | Bean Validation (Jakarta)           |
| Build Tool        | Maven                               |
| Testing           | JUnit 5, Mockito, MockMvc, H2       |
| API Documentation | README + Postman                    |

---

## ✨ Features

* 🔐 **JWT Authentication** — Stateless login with signed tokens and 24-hour expiry
* 👥 **Role-Based Access Control** — Supports `ADMIN` and `USER` roles
* 📦 **Resource CRUD** — Admin can manage bookable resources
* 📅 **Reservation Management** — Create, update, and delete reservations
* 🚫 **Double-Booking Prevention** — Prevents overlapping reservations
* 🔒 **Ownership Enforcement** — Users can access only their own reservations
* 💰 **Price Snapshots** — Reservation stores the resource price at booking time
* 🔍 **Dynamic Filtering** — Filter by status, minimum price, and maximum price
* 📄 **Pagination & Sorting** — Supports page, size, and whitelisted sort fields
* ✅ **Bean Validation** — Request validation with structured JSON errors
* 💾 **Seed Data** — Demo users and resources are inserted automatically

---

## 📁 Project Structure

```text
src/
├── main/
│   ├── java/com/example/booking/
│   │   ├── config/
│   │   │   └── DataSeeder.java
│   │   │
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── ResourceController.java
│   │   │   └── ReservationController.java
│   │   │
│   │   ├── dto/
│   │   │   ├── Request DTOs
│   │   │   ├── Response DTOs
│   │   │   └── PageResponse
│   │   │
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Resource.java
│   │   │   ├── Reservation.java
│   │   │   └── Enums
│   │   │
│   │   ├── exception/
│   │   │   ├── Custom Exceptions
│   │   │   └── GlobalExceptionHandler
│   │   │
│   │   ├── repository/
│   │   │   └── Spring Data JPA Repositories
│   │   │
│   │   ├── security/
│   │   │   ├── JwtService.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── SecurityConfig.java
│   │   │   ├── UserDetailsImpl.java
│   │   │   ├── UserDetailsService.java
│   │   │   ├── JwtAuthenticationEntryPoint.java
│   │   │   └── AccessDeniedHandler
│   │   │
│   │   ├── service/
│   │   │   ├── Business Logic
│   │   │   └── ReservationSpecification
│   │   │
│   │   └── BookingApplication.java
│   │
│   └── resources/
│       └── application.properties
│
└── test/
    ├── java/
    │   ├── Unit Tests
    │   └── Integration Tests
    │
    └── resources/
        └── application.properties
```

---

# 🚀 Setup Instructions

## Prerequisites

Make sure the following are installed:

* Java 17 or later
* Maven 3.8+
* MySQL 8.x
* IntelliJ IDEA / Eclipse / VS Code

---

## Step 1 — Create the Database

Open MySQL and run:

```sql
CREATE DATABASE booking_db;
```

---

## Step 2 — Configure Database Credentials

Open:

```text
src/main/resources/application.properties
```

Configure your MySQL connection:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/booking_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

> ⚠️ Do not commit your real database password to GitHub.

---

## Step 3 — Run the Application

Using Maven:

```bash
mvn spring-boot:run
```

Or run:

```text
BookingApplication.java
```

from your IDE.

The application will start at:

```text
http://localhost:8080
```

Hibernate automatically creates/updates the database schema, and the `DataSeeder` inserts demo data when the tables are empty.

---

# 🗄 Database Configuration

| Property      | Value                                |
| ------------- | ------------------------------------ |
| Database      | MySQL 8.x                            |
| Database Name | `booking_db`                         |
| DDL Mode      | `update`                             |
| Tables        | `users`, `resources`, `reservations` |
| Price Type    | `DECIMAL(10,2)`                      |
| Server Port   | `8080`                               |

### Entity Relationship

```text
User
 │
 │ 1
 │
 └──────────< Reservation >──────────┐
                                      │
                                      │ 1
                                      │
                                   Resource
```

A reservation contains:

* `startTime`
* `endTime`
* `price`
* `status`
* `createdAt`

The reservation price is stored as a snapshot of the resource price at booking time.

---

# ⚙️ Environment Variables

For production deployments, sensitive configuration can be supplied through environment variables.

| Property                     | Environment Variable         | Description        |
| ---------------------------- | ---------------------------- | ------------------ |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | MySQL username     |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | MySQL password     |
| `app.jwt.secret`             | `APP_JWT_SECRET`             | Base64 HMAC secret |
| `app.jwt.expiration-ms`      | `APP_JWT_EXPIRATION_MS`      | JWT lifetime       |

Example:

```bash
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export APP_JWT_SECRET=your_base64_secret_here
export APP_JWT_EXPIRATION_MS=86400000

mvn spring-boot:run
```

---

# 👤 Seed Users

Demo users are automatically created when the users table is empty.

| Username | Password   | Role  | Capabilities                                               |
| -------- | ---------- | ----- | ---------------------------------------------------------- |
| `admin`  | `admin123` | ADMIN | Full resource & reservation management                     |
| `john`   | `john123`  | USER  | View resources, create reservations, view own reservations |

### Registration

New users can register using:

```http
POST /auth/register
```

Newly registered users are always assigned the `USER` role.

Clients cannot register themselves as `ADMIN`.

---

# 📚 API Documentation

## 🔐 Authentication

### Login

```http
POST /auth/login
```

Request:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Response:

```text
200 OK
JWT Token
```

### Register

```http
POST /auth/register
```

Request:

```json
{
  "username": "newuser",
  "password": "password123"
}
```

Response:

```text
201 Created
JWT Token
```

---

# 📦 Resources API

Base URL:

```text
/resources
```

| Method | Endpoint          | Access      |
| ------ | ----------------- | ----------- |
| GET    | `/resources`      | USER, ADMIN |
| GET    | `/resources/{id}` | USER, ADMIN |
| POST   | `/resources`      | ADMIN       |
| PUT    | `/resources/{id}` | ADMIN       |
| DELETE | `/resources/{id}` | ADMIN       |

### Create Resource

```http
POST /resources
```

Request:

```json
{
  "name": "Conference Room A",
  "description": "Seats 12, whiteboard, video screen",
  "type": "ROOM",
  "available": true,
  "price": 1500.00
}
```

### Validation Rules

* `name` is required
* `type` is required
* `name` maximum length: 100 characters
* `type` maximum length: 50 characters
* `price` must be greater than `0`
* Maximum `2` decimal places for price
* `available` is required

---

# 📅 Reservations API

Base URL:

```text
/reservations
```

| Method | Endpoint             | Access                   |
| ------ | -------------------- | ------------------------ |
| GET    | `/reservations`      | USER → own / ADMIN → all |
| GET    | `/reservations/{id}` | USER → own / ADMIN → all |
| POST   | `/reservations`      | USER, ADMIN              |
| PUT    | `/reservations/{id}` | ADMIN                    |
| DELETE | `/reservations/{id}` | ADMIN                    |

### Create Reservation

```http
POST /reservations
```

Request:

```json
{
  "resourceId": 1,
  "startTime": "2026-09-01T10:00:00",
  "endTime": "2026-09-01T14:00:00"
}
```

> **Important:** There is no `userId` in the request. The reservation owner is automatically identified from the JWT token.

---

## 📌 Reservation Business Rules

1. New reservations always start as `PENDING`.
2. Clients cannot directly create a `CONFIRMED` reservation.
3. `startTime` must be before `endTime`.
4. Reservation time must be in the future.
5. The resource must exist.
6. The resource must be available.
7. Overlapping reservations for the same resource are rejected.
8. Cancelled reservations do not block the time slot.
9. Admins can change reservation status to `CONFIRMED` or `CANCELLED`.
10. Reservation price is copied from the resource when the booking is created.

---

# 🔍 Filtering, Pagination & Sorting

Reservations support dynamic filtering.

### Filter by Status

```http
GET /reservations?status=CONFIRMED
```

### Filter by Price

```http
GET /reservations?minPrice=1000&maxPrice=5000
```

### Pagination

```http
GET /reservations?page=0&size=10
```

### Sorting

```http
GET /reservations?page=0&size=10&sort=price,desc
```

### Combined Query

```http
GET /reservations?status=PENDING&minPrice=500&page=0&size=5&sort=startTime,desc
```

### Query Parameters

| Parameter  | Default  | Description                         |
| ---------- | -------- | ----------------------------------- |
| `status`   | —        | `PENDING`, `CONFIRMED`, `CANCELLED` |
| `minPrice` | —        | Minimum reservation price           |
| `maxPrice` | —        | Maximum reservation price           |
| `page`     | `0`      | Zero-based page number              |
| `size`     | `10`     | Number of records per page          |
| `sort`     | `id,asc` | Sorting field and direction         |

### Allowed Sort Fields

```text
id
startTime
endTime
price
status
createdAt
```

---

# 📄 Paginated Response

Example:

```json
{
  "content": [
    {
      "id": 1,
      "username": "john",
      "resourceId": 1,
      "resourceName": "Conference Room A",
      "startTime": "2026-09-01T10:00:00",
      "endTime": "2026-09-01T14:00:00",
      "price": 6000.00,
      "status": "PENDING",
      "createdAt": "2026-08-29T10:15:30"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

# ❌ Error Responses

All errors are returned as structured JSON.

Example:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Reservation not found with id: 99",
  "path": "/reservations/99",
  "timestamp": "2026-08-29T15:30:45.123456"
}
```

| Status Code | Description                                                            |
| ----------- | ---------------------------------------------------------------------- |
| `400`       | Validation failure, malformed JSON, invalid enum/parameter             |
| `401`       | Missing, invalid, or expired JWT                                       |
| `403`       | Insufficient permissions or unauthorized resource access               |
| `404`       | Requested entity does not exist                                        |
| `409`       | Double-booking, unavailable resource, invalid time, duplicate username |
| `500`       | Unexpected server error                                                |

---

# 🔒 Security Design

### Stateless JWT Authentication

The application uses stateless JWT authentication.

Every secured request must contain:

```http
Authorization: Bearer <token>
```

JWT tokens are:

* HMAC-SHA256 signed
* Valid for 24 hours by default
* Verified on every secured request

### Identity From JWT

The reservation owner is resolved from the authenticated JWT instead of the request body.

This prevents users from creating or accessing reservations on behalf of another user.

### Role-Based Authorization

Two roles are supported:

```text
ADMIN
USER
```

Admins have full management permissions, while regular users have restricted access.

### Password Security

Passwords are securely hashed using:

```text
BCrypt
```

### Ownership Enforcement

Users can only view their own reservations.

Admins can view all reservations.

### Generic Login Errors

Invalid usernames and incorrect passwords return the same error message:

```text
Invalid username or password
```

This helps prevent username enumeration.

### Sort Whitelisting

Only approved fields can be used for sorting, preventing unauthorized property probing through the `sort` parameter.

---

# 🧪 Running Tests

Run the complete test suite:

```bash
mvn test
```

The project contains **36 tests** across three test classes.

| Test Class                      | Type                       | Coverage                                                     |
| ------------------------------- | -------------------------- | ------------------------------------------------------------ |
| `ReservationServiceTest`        | Unit / Mockito             | Business rules, overlap detection, price snapshot, ownership |
| `SecurityIntegrationTest`       | Integration / MockMvc + H2 | Authentication, RBAC, ownership                              |
| `ReservationApiIntegrationTest` | Integration / MockMvc + H2 | Filtering, pagination, sorting, double-booking               |

Integration tests use an **in-memory H2 database**, so your actual MySQL database is not modified during testing.

---

# 📬 Example Requests

## 1. Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

## 2. Create Reservation

Using John's JWT token:

```bash
curl -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": 1,
    "startTime": "2026-09-01T10:00:00",
    "endTime": "2026-09-01T14:00:00"
  }'
```

---

## 3. List Reservations

Filtered, paginated, and sorted:

```bash
curl -X GET "http://localhost:8080/reservations?status=PENDING&minPrice=500&maxPrice=2000&page=0&size=10&sort=price,desc" \
  -H "Authorization: Bearer <token>"
```

---

# 🏁 Quick Start

```bash
# 1. Create database
mysql -u root -p -e "CREATE DATABASE booking_db;"

# 2. Configure MySQL password
# Edit:
# src/main/resources/application.properties

# 3. Start application
mvn spring-boot:run

# 4. Login using seeded credentials
# ADMIN
admin / admin123

# USER
john / john123

# 5. Run tests
mvn test
```

---

## 📌 Important Notes

* Never commit real passwords or JWT secrets.
* Use environment variables for production credentials.
* The application runs on port `8080` by default.
* MySQL is used for the main application.
* H2 is used for integration testing.
* Reservation ownership is determined from the JWT.
* Double-booking prevention is enforced at the service/business-logic level.
* Newly registered users always receive the `USER` role.

---

## 👨‍💻 Project Summary

This project demonstrates a production-style Spring Boot REST API with:

**Spring Boot + Java 17 + Spring Security + JWT + JPA/Hibernate + MySQL + REST APIs + Role-Based Access Control + Validation + Pagination + Filtering + Integration Testing**

---

### ⭐ Key Highlights

* Secure JWT-based authentication
* ADMIN / USER authorization
* RESTful API architecture
* Double-booking prevention
* Reservation ownership protection
* Dynamic filtering and pagination
* Price snapshot mechanism
* Global exception handling
* Bean validation
* Unit and integration testing
* H2 test database
* MySQL production database
