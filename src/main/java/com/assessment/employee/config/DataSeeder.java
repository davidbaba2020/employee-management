package com.assessment.employee.config;

import com.assessment.employee.entity.Employee;
import com.assessment.employee.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Populates the database with deterministic seed data on startup.
 *
 * <p>Skips seeding if records already exist, so it is safe to restart
 * the application without duplicating data (useful when ddl-auto is not create-drop).
 *
 * <p>Covers the following test scenarios:
 * <ul>
 *   <li>Multiple departments — Engineering, HR, Finance, Marketing, Sales, Operations, Intern</li>
 *   <li>Salary boundary cases — just above the floor for each department tier</li>
 *   <li>Inactive (soft-deleted) employees — for filter / restore tests</li>
 *   <li>Varied join dates — spread across several years for date-range tests</li>
 *   <li>Enough rows (25) to exercise pagination at page sizes of 5 and 10</li>
 * </ul>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final EmployeeRepository employeeRepository;

    public DataSeeder(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0) {
            log.info("DataSeeder — records already present, skipping seed.");
            return;
        }

        List<Employee> employees = List.of(

            // ── Engineering (salary floor 30 000) ───────────────────────────
            employee("Alice",    "Thompson",  "alice.thompson@company.com",   "Engineering", 95000, "2019-03-15", true),
            employee("Bob",      "Martinez",  "bob.martinez@company.com",     "Engineering", 82000, "2020-07-01", true),
            employee("Clara",    "Nguyen",    "clara.nguyen@company.com",     "Engineering", 30500, "2023-11-20", true),  // just above floor
            employee("Derek",    "Patel",     "derek.patel@company.com",      "Engineering", 110000,"2017-05-10", true),
            employee("Eva",      "Schmidt",   "eva.schmidt@company.com",      "Engineering", 76000, "2021-09-03", false), // inactive — soft-deleted

            // ── HR ───────────────────────────────────────────────────────────
            employee("Frank",    "Johnson",   "frank.johnson@company.com",    "HR",          55000, "2018-01-22", true),
            employee("Grace",    "Lee",       "grace.lee@company.com",        "HR",          48000, "2022-04-14", true),
            employee("Henry",    "Wilson",    "henry.wilson@company.com",     "HR",          31000, "2023-08-30", true),  // just above floor

            // ── Finance ──────────────────────────────────────────────────────
            employee("Irene",    "Bakker",    "irene.bakker@company.com",     "Finance",     88000, "2016-11-05", true),
            employee("James",    "O'Brien",   "james.obrien@company.com",     "Finance",     72000, "2019-06-18", true),
            employee("Karen",    "Yamamoto",  "karen.yamamoto@company.com",   "Finance",     30000, "2024-01-08", false), // inactive — salary at exact floor

            // ── Marketing ────────────────────────────────────────────────────
            employee("Liam",     "Costa",     "liam.costa@company.com",       "Marketing",   60000, "2020-02-29", true),  // leap-day join
            employee("Mia",      "Rossi",     "mia.rossi@company.com",        "Marketing",   54000, "2021-12-01", true),
            employee("Nathan",   "Brown",     "nathan.brown@company.com",     "Marketing",   41000, "2022-07-19", true),

            // ── Sales ────────────────────────────────────────────────────────
            employee("Olivia",   "Chen",      "olivia.chen@company.com",      "Sales",       67000, "2018-09-11", true),
            employee("Peter",    "Müller",    "peter.muller@company.com",     "Sales",       59000, "2020-03-25", true),
            employee("Quinn",    "Davis",     "quinn.davis@company.com",      "Sales",       35000, "2023-05-07", false), // inactive

            // ── Operations ───────────────────────────────────────────────────
            employee("Rachel",   "Kim",       "rachel.kim@company.com",       "Operations",  47000, "2019-10-14", true),
            employee("Samuel",   "Okafor",    "samuel.okafor@company.com",    "Operations",  43000, "2021-04-02", true),
            employee("Tina",     "Fernandez", "tina.fernandez@company.com",   "Operations",  38000, "2022-11-28", true),

            // ── Intern (salary floor 15 000) ─────────────────────────────────
            employee("Uma",      "Patel",     "uma.patel@company.com",        "Intern",      18000, "2024-06-01", true),
            employee("Victor",   "Santos",    "victor.santos@company.com",    "Intern",      15500, "2024-06-01", true),  // just above intern floor
            employee("Wendy",    "Tanaka",    "wendy.tanaka@company.com",     "Intern",      16000, "2025-01-13", true),
            employee("Xavier",   "Dubois",    "xavier.dubois@company.com",    "Intern",      15000, "2025-03-03", false), // inactive intern — salary at floor
            employee("Yara",     "Novak",     "yara.novak@company.com",       "Intern",      17500, "2025-04-21", true)
        );

        employeeRepository.saveAll(employees);
        log.info("DataSeeder — inserted {} seed employees.", employees.size());
    }

    private static Employee employee(String firstName, String lastName, String email,
                                     String department, double salary,
                                     String joinDate, boolean active) {
        return Employee.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .department(department)
                .salary(BigDecimal.valueOf(salary))
                .dateOfJoining(LocalDate.parse(joinDate))
                .active(active)
                .build();
    }
}
