package com.assessment.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Employee Management System.
 *
 * <p>This application exposes a RESTful API for managing employees, including:
 * <ul>
 *   <li>Full CRUD operations</li>
 *   <li>Bulk Excel import / export</li>
 *   <li>PDF report generation</li>
 *   <li>Email notifications</li>
 * </ul>
 *
 * <p>H2 console: <a href="http://localhost:8080/h2-console">http://localhost:8080/h2-console</a>
 * <p>Swagger UI : <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a>
 */
@SpringBootApplication
@EnableAsync
public class EmployeeManagementApplication {

    private static final Logger log = LoggerFactory.getLogger(EmployeeManagementApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EmployeeManagementApplication.class, args);
        log.info("=============================================================");
        log.info("  Employee Management System started successfully");
        log.info("  H2 Console : http://localhost:8080/h2-console");
        log.info("  Swagger UI : http://localhost:8080/swagger-ui.html");
        log.info("  API Base   : http://localhost:8080/api/v1/employees");
        log.info("=============================================================");
    }
}
