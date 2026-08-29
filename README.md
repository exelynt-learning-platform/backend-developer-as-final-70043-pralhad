# backend-developer-as-final-70043-pralhad
Final Project Assignment - This repository contains the complete final project code and documentation.

🎯 Resource Booking System
A secure, RESTful Resource Booking System built with Spring Boot, featuringJWT authentication, role-based access control (ADMIN/USER), reservation managementwith double-booking prevention, dynamic filtering, pagination, and sorting.

📋 Table of Contents
Tech Stack
Features
Project Structure
Setup Instructions
Database Configuration
Environment Variables
Seed Users (Test Credentials)
API Documentation
Security Design
Running Tests
Example Requests

🛠 Tech Stack
Component	Technology
Language	Java 17
Framework	Spring Boot 3.5.0
Security	Spring Security 6 + JWT (JJWT 0.12)
Persistence	Spring Data JPA / Hibernate
Database	MySQL 8.x
Validation	Bean Validation (Jakarta)
Build Tool	Maven
Testing	JUnit 5, Mockito, MockMvc, H2
API Docs	This README + Postman

✨ Features
🔐 JWT Authentication — stateless login with signed tokens (24h expiry)
👥 Role-Based Access Control — ADMIN and USER roles with endpoint-level authorization
📦 Resource CRUD — ADMIN manages bookable resources (rooms, vehicles, equipment)
📅 Reservation Management — create, update (ADMIN), delete (ADMIN)
🚫 Double-Booking Prevention — overlap detection with time-range math
🔒 Ownership Enforcement — USER sees only their own reservations; ADMIN sees all
🧾 Price Snapshots — reservation stores the price at booking time (BigDecimal)
🔍 Filtering — by status, minPrice, maxPrice (freely combinable)
📄 Pagination & Sorting — page, size, sort with whitelisted sort fields
✅ Bean Validation — full request validation with clean JSON error responses
💾 Seed Data — demo users and resources inserted automatically on first startup


📁 Project Structure
src/main/java/com/example/booking/├── config/               # DataSeeder — seed users & resources at startup├── controller/           # REST controllers (Auth, Resource, Reservation)├── dto/                  # Request/Response DTOs + PageResponse wrapper├── entity/               # JPA entities (User, Resource, Reservation, enums)├── exception/            # Custom exceptions + GlobalExceptionHandler├── repository/           # Spring Data JPA repositories├── security/             # JwtService, JwtAuthenticationFilter, SecurityConfig,│                         # UserDetailsImpl(+Service), 401/403 JSON handlers├── service/              # Business logic + ReservationSpecification└── BookingApplication.javasrc/main/resources/       # application.properties (MySQL config)src/test/resources/       # application.properties (H2 in-memory test config)src/test/java/            # Unit tests (Mockito) + Integration tests (MockMvc + H2)
🚀 Setup Instructions
Prerequisites
Java 17 or later
Maven 3.8+
MySQL 8.x running locally
Step 1 — Create the database
Open MySQL and run:

CREATE DATABASE booking_db;
Step 2 — Configure your credentials
Edit src/main/resources/application.properties and set your MySQL password:

spring.datasource.url=jdbc:mysql://localhost:3306/booking_db?createDatabaseIfNotExist=truespring.datasource.username=rootspring.datasource.password=YOUR_MYSQL_PASSWORD
Step 3 — Run the application
mvn spring-boot:run
Or run the BookingApplication main class from your IDE.

The app starts on http://localhost:8080. Hibernate auto-creates the tables(ddl-auto=update), and the DataSeeder inserts the demo users and resourceson first startup (only if the tables are empty — safe on every restart).

🗄 Database Configuration
Property	Value
URL	jdbc:mysql://localhost:3306/booking_db
DDL mode	update (schema auto-created/updated on startup)
Tables	users, resources, reservations
Relations	reservations.user_id → users.id and reservations.resource_id → resources.id (ManyToOne)
Price type	DECIMAL(10,2) — never floating point
Entity Relationship Overview
User (1) ──────< Reservation >────── (1) Resource                    │                    ├── startTime / endTime (DATETIME)                    ├── price        (DECIMAL(10,2), snapshot at booking time)                    ├── status       (PENDING / CONFIRMED / CANCELLED)                    └── createdAt    (set automatically via @PrePersist)
⚙ Environment Variables
All configuration lives in application.properties. For production deployments,override secrets via environment variables:

Property	Env Variable	Description
spring.datasource.username	SPRING_DATASOURCE_USERNAME	MySQL username
spring.datasource.password	SPRING_DATASOURCE_PASSWORD	MySQL password
app.jwt.secret	APP_JWT_SECRET	Base64 HMAC secret (min 256-bit)
app.jwt.expiration-ms	APP_JWT_EXPIRATION_MS	Token lifetime (default: 86400000 = 24 h)
Example:

export SPRING_DATASOURCE_PASSWORD=your_passwordexport APP_JWT_SECRET=your_base64_secret_heremvn spring-boot:run
👤 Seed Users (Test Credentials)
Created automatically at startup (only when the users table is empty):

Username	Password	Role	Capabilities
admin	admin123	ADMIN	Full CRUD on resources & reservations; views ALL reservations
john	john123	USER	Views resources; creates reservations; views OWN reservations only
New users can self-register via POST /auth/register.Registrants are always assigned the USER role — clients cannot request ADMIN.

📚 API Documentation
All secured endpoints require the header:

Authorization: Bearer <token>
Authentication — /auth
Method	Endpoint	Access	Request Body	Success
POST	/auth/login	Public	{"username", "password"}	200 + JWT
POST	/auth/register	Public	{"username", "password"}	201 + JWT
Resources — /resources
Method	Endpoint	Access	Success
GET	/resources	USER, ADMIN	200 — list all
GET	/resources/{id}	USER, ADMIN	200 — single
POST	/resources	ADMIN only	201 + Location
PUT	/resources/{id}	ADMIN only	200
DELETE	/resources/{id}	ADMIN only	204
Resource request body:

{  "name": "Conference Room A",  "description": "Seats 12, whiteboard, video screen",  "type": "ROOM",  "available": true,  "price": 1500.00}
Validation: name and type required (max 100/50 chars), price > 0 with atmost 2 decimal places, available required.

Reservations — /reservations
Method	Endpoint	Access	Success
GET	/reservations	USER → own only / ADMIN → all	200 (paged)
GET	/reservations/{id}	USER → own only / ADMIN → all	200
POST	/reservations	USER, ADMIN	201 + Location
PUT	/reservations/{id}	ADMIN only	200
DELETE	/reservations/{id}	ADMIN only	204
Reservation request body — note there is no userId field; the owner isalways resolved from the JWT:

{  "resourceId": 1,  "startTime": "2026-09-01T10:00:00",  "endTime": "2026-09-01T14:00:00"}
Business rules:

New reservations always start as PENDING (a client cannot self-confirm)
startTime must be before endTime and in the future
The resource must exist and be available = true
The time slot must not overlap another non-cancelled reservation for thesame resource (→ 409 Conflict)
On ADMIN update, an optional "status" field transitions the reservationto CONFIRMED or CANCELLED
The reservation price is copied from the resource at booking time
Filtering, Pagination & Sorting
GET /reservations?status=CONFIRMEDGET /reservations?minPrice=1000&maxPrice=5000GET /reservations?page=0&size=10GET /reservations?page=0&size=10&sort=price,descGET /reservations?status=PENDING&minPrice=500&page=0&size=5&sort=startTime,desc
Param	Default	Notes
status	—	Enum: PENDING, CONFIRMED, CANCELLED (invalid → 400)
minPrice	—	Decimal lower bound (price >= minPrice)
maxPrice	—	Decimal upper bound (price <= maxPrice)
page	0	Zero-based page index
size	10	Items per page
sort	id,asc	Whitelisted fields: id, startTime, endTime, price, status, createdAt
Paginated response shape:

{  "content": [    {      "id": 1,      "username": "john",      "resourceId": 1,      "resourceName": "Conference Room A",      "startTime": "2026-09-01T10:00:00",      "endTime": "2026-09-01T14:00:00",      "price": 6000.00,      "status": "PENDING",      "createdAt": "2026-08-29T10:15:30"    }  ],  "page": 0,  "size": 10,  "totalElements": 1,  "totalPages": 1,  "last": true}
Error Responses
Every error is returned as clean JSON:

{  "status": 404,  "error": "Not Found",  "message": "Reservation not found with id: 99",  "path": "/reservations/99",  "timestamp": "2026-08-29T15:30:45.123456"}
Code	When
400	Validation failure, malformed JSON, invalid enum/param value
401	Missing, invalid, or expired token; wrong login credentials
403	Authenticated but insufficient role, or accessing others' data
404	Requested entity does not exist
409	Double-booking, unavailable resource, invalid times, duplicate username
500	Unexpected server error (details logged server-side only)
🔒 Security Design
Stateless JWT — no server-side sessions; every request is verified viathe Authorization: Bearer header (HMAC-SHA256 signed, 24h expiry).
Identity comes from the token, never the request body —ReservationRequest has no userId field. The owner is resolved from theJWT inside ReservationService, making it impossible for one user to createor read reservations on behalf of another.
Registration cannot grant roles — RegisterRequest has no role field;self-registered users are always assigned USER.
BCrypt password hashing — one-way, salted per user.
Ownership enforced at query level — USER list queries automatically geta forced user_id = ? filter (JPA Specification); ADMIN skips it.
Generic login errors — the same "Invalid username or password" messagefor both unknown users and wrong passwords (prevents username enumeration).
Sort-field whitelist — prevents property probing via ?sort=.

🧪 Running Tests
mvn test
36 tests across three classes:

Class	Type	Coverage
ReservationServiceTest	Unit (Mockito)	Business rules: overlap rejection, forced PENDING status, price snapshot, ownership checks, time validation
SecurityIntegrationTest	Integration (MockMvc+H2)	401 without/with invalid token, 403 RBAC cases, USER sees only own data, userId-in-body ignored, ADMIN sees all
ReservationApiIntegrationTest	Integration (MockMvc+H2)	Filtering, pagination, sorting, 409 double-booking, status transitions, cancelled-slot rebooking
Integration tests run against an in-memory H2 database(src/test/resources/application.properties) — your real MySQL data is never touched.

📬 Example Requests
1. Login:

curl -X POST http://localhost:8080/auth/login 
\  -H "Content-Type: application/json" \  -d '{"username": "admin", "password": "admin123"}'
2. Create a reservation (as john):

curl -X POST http://localhost:8080/reservations 
\  -H "Authorization: Bearer <token>" \  -H "Content-Type: application/json" \  -d '{"resourceId": 1, "startTime": "2026-09-01T10:00:00", "endTime": "2026-09-01T14:00:00"}'
3. List reservations — filtered, paginated, sorted:

curl -X GET "http://localhost:8080/reservations?status=PENDING&minPrice=500&maxPrice=2000&page=0&size=10&sort=price,desc" \  -H "Authorization: Bearer <token>"
🏁 Quick Start Summary
# 1. Create DBmysql -u root -p -e "CREATE DATABASE booking_db;"# 2. Set your MySQL password in application.properties# 3. Runmvn spring-boot:run# 4. Login (seeded credentials)#    admin / admin123  (ADMIN)#    john  / john123   (USER)# 5. Run the test suitemvn test

📖 Notes on the Formatting
Choice
Why
4-backtick outer wrapper ( ````)	Lets you copy the whole file while keeping the inner ``` code blocks intact — a plain triple-backtick block would break at the first nested fence
text language on the structure tree	Renders the ASCII tree in a monospace block without syntax-highlighting artifacts
Anchored table of contents	GitHub auto-links the headings — clickable navigation
Tables for everything	Evaluators skim — tables read 5× faster than paragraphs
YOUR_MYSQL_PASSWORD placeholder	Never commit real passwords — this is also why the env-var section exists
