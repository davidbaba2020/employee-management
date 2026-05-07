# Employee Management System

A production-ready Spring Boot REST API for managing employee records, featuring full CRUD, Excel import/export, PDF report generation, and email notifications.

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.5 (upgrade to 3.5.x when available) |
| Database | H2 In-Memory | Bundled |
| ORM | Spring Data JPA / Hibernate | Bundled |
| Validation | Jakarta Bean Validation | Bundled |
| Excel I/O | Apache POI | 5.3.0 |
| PDF Generation | OpenPDF | 1.3.43 |
| API Docs | SpringDoc OpenAPI | 2.6.0 |
| Logging | SLF4J + Logback | Bundled |

---

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- No database setup required — H2 runs in-memory

---

## Quick Start

### 1. Clone / unzip the project

```bash
unzip employee-management.zip
cd employee-management
```

### 2. Build and run

```bash
# Using Maven wrapper (no local Maven required)
./mvnw spring-boot:run

# Or build a JAR first
./mvnw clean package -DskipTests
java -jar target/employee-management-1.0.0.jar
```

The application starts on **http://localhost:8080**.

### 3. Access the tools

| Tool | URL |
|---|---|
| Swagger UI (try all endpoints) | http://localhost:8080/swagger-ui.html |
| H2 Database Console | http://localhost:8080/h2-console |
| API Docs (JSON) | http://localhost:8080/api-docs |
| Application logs | `logs/employee-management.log` |

**H2 Console connection details:**
- JDBC URL: `jdbc:h2:mem:employeedb`
- User Name: `sa`
- Password: *(leave blank)*

---

## API Reference

### Base URL: `/api/v1/employees`

| Method | Path | Description | Status |
|---|---|---|---|
| POST | `/` | Create employee | 201 |
| GET | `/` | List employees (paginated, filterable) | 200 |
| GET | `/{id}` | Get by ID | 200 / 404 |
| PUT | `/{id}` | Full update | 200 |
| PATCH | `/{id}` | Partial update (salary, dept, active) | 200 |
| DELETE | `/{id}` | Soft delete (active=false) | 204 |
| DELETE | `/{id}/hard` | Hard delete (must be inactive) | 204 |
| GET | `/salary-range?min=&max=` | Filter by salary | 200 |
| POST | `/import` (multipart) | Bulk import from .xlsx | 200 / 207 |
| GET | `/export/excel` | Download .xlsx | 200 |
| GET | `/export/pdf` | Download PDF report | 200 |

#### Query Parameters for GET `/`

| Param | Type | Default | Description |
|---|---|---|---|
| `department` | String | — | Filter by department |
| `active` | Boolean | — | Filter by status |
| `page` | int | 0 | Page number (0-based) |
| `size` | int | 10 | Items per page |
| `sort` | String | id | Sort field |
| `direction` | String | asc | `asc` or `desc` |

---

## Sample Requests (curl)

### Create an employee
```bash
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Johnson",
    "email": "alice.johnson@example.com",
    "department": "Engineering",
    "salary": 85000.00,
    "dateOfJoining": "2023-03-15",
    "active": true
  }'
```

### Get all employees (page 0, size 5, sorted by lastName)
```bash
curl "http://localhost:8080/api/v1/employees?page=0&size=5&sort=lastName"
```

### Import from Excel
```bash
curl -X POST http://localhost:8080/api/v1/employees/import \
  -F "file=@employees_template.xlsx"
```

### Export to Excel
```bash
curl -o employees.xlsx http://localhost:8080/api/v1/employees/export/excel
```

### Export to PDF
```bash
curl -o report.pdf http://localhost:8080/api/v1/employees/export/pdf
```

---

## Excel Import Template

The `.xlsx` file must have these columns (Row 1 = header, data starts at Row 2):

| Col | Header | Format | Example |
|---|---|---|---|
| A | firstName | Text | Alice |
| B | lastName | Text | Johnson |
| C | email | Email | alice@example.com |
| D | department | Text | Engineering |
| E | salary | Number | 75000 |
| F | dateOfJoining | YYYY-MM-DD | 2023-03-15 |
| G | active | TRUE/FALSE | TRUE |

---

## Email Notifications

Email is **disabled by default** (dev mode). To see email content, check the application logs — they are printed with `[EMAIL-MOCK]` prefix.

To enable real SMTP, edit `application.properties`:

```properties
app.email.enabled=true
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

**Notification events:**
- Employee created → Welcome email to the employee
- Employee soft-deleted → Deactivation notice to the employee
- Excel import complete → Summary email to the system address

---

## Business Rules

| Rule | Details |
|---|---|
| Email uniqueness | No two employees may share the same email |
| Salary floor — Intern dept | Minimum salary: 15,000 |
| Salary floor — All other depts | Minimum salary: 30,000 |
| Soft delete | DELETE sets `active=false`; record is retained |
| Hard delete | Only allowed when `active=false` |
| Excel import | Only `.xlsx` accepted; row-level errors are reported without aborting |

---

## Project Structure

```
src/main/java/com/assessment/employee/
├── EmployeeManagementApplication.java   # Entry point
├── config/
│   ├── AsyncConfig.java                 # Thread pool for async email
│   └── OpenApiConfig.java              # Swagger / API docs
├── controller/
│   └── EmployeeController.java         # Thin REST layer — no business logic
├── dto/
│   ├── request/
│   │   ├── EmployeeRequestDto.java     # Create / full-update payload
│   │   └── EmployeePatchDto.java       # Partial update payload
│   └── response/
│       ├── ApiResponse.java            # Generic <T> response wrapper
│       ├── EmployeeResponseDto.java    # Employee output DTO
│       ├── ImportResultDto.java        # Excel import result
│       └── PagedResponse.java          # Paginated list wrapper
├── entity/
│   └── Employee.java                   # JPA entity with lifecycle hooks
├── exception/
│   ├── DuplicateEmailException.java    # → 409
│   ├── EmployeeNotFoundException.java  # → 404
│   ├── ExcelProcessingException.java   # → 500
│   ├── InvalidFileFormatException.java # → 400
│   └── GlobalExceptionHandler.java    # @RestControllerAdvice
├── repository/
│   └── EmployeeRepository.java        # Spring Data JPA + custom queries
├── service/
│   ├── EmployeeService.java           # Interface with full JavaDoc
│   ├── ExcelService.java
│   ├── PdfService.java
│   ├── EmailService.java
│   └── impl/
│       ├── EmployeeServiceImpl.java   # Business logic
│       ├── ExcelServiceImpl.java      # Apache POI
│       ├── PdfServiceImpl.java        # OpenPDF
│       └── EmailServiceImpl.java      # Spring Mail
└── util/
    └── CellExtractorUtil.java         # POI cell type normalisation
```

---

## Running Tests

```bash
./mvnw test
```

Tests use an isolated H2 in-memory database. Email is always mocked in tests.

---

## Configuration Reference

All tuneable settings are in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | 8080 | HTTP port |
| `app.email.enabled` | false | Send real emails when true |
| `app.business.salary.floor.default` | 30000 | Minimum salary (non-intern) |
| `app.business.salary.floor.intern` | 15000 | Minimum salary (Intern dept) |
| `app.async.core-pool-size` | 2 | Async email thread pool core size |
| `spring.servlet.multipart.max-file-size` | 10MB | Max upload size |

---

## Notes for Upgrading

- To use Spring Boot 3.5.x, change the `<version>` in `pom.xml` and verify all transitive dependency compatibility.
- The `springdoc-openapi-starter-webmvc-ui` version may need updating alongside Spring Boot.

---

*Built for the Spring Boot Developer Assessment — Full-Stack CRUD with Excel/PDF*
