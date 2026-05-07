package com.assessment.employee;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring context loads without errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmployeeManagementApplicationTests {

    @Test
    void contextLoads() {
        // If the application context fails to start, this test will fail.
    }
}
