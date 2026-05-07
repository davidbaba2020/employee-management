package com.assessment.employee.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger documentation configuration.
 * Access the generated UI at /swagger-ui.html once the app is running.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI employeeManagementOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Contact contact = new Contact();
        contact.setName("Assessment Team");
        contact.setEmail("support@employeemgmt.com");

        License license = new License();
        license.setName("MIT License");
        license.setUrl("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("Employee Management API")
                .version("1.0.0")
                .description("""
                        RESTful API for the Employee Management System.

                        **Features:**
                        - Full CRUD for Employee records
                        - Bulk import from `.xlsx` files
                        - Export employees to Excel or PDF
                        - Email notifications on key events

                        **H2 Console:** http://localhost:8080/h2-console
                        (JDBC URL: `jdbc:h2:mem:employeedb`, User: `sa`, Password: *(empty)*)
                        """)
                .contact(contact)
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
