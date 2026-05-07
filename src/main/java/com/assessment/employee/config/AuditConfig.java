package com.assessment.employee.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing ({@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy}, {@code @LastModifiedBy}) for all entities that extend
 * {@link com.assessment.employee.entity.BaseEntity}.
 *
 * <p>The {@link AuditorAware} bean currently returns {@code "system"} because this
 * application has no authentication layer yet.  When Spring Security is added,
 * replace the lambda body with:
 * <pre>
 *   SecurityContextHolder.getContext().getAuthentication().getName()
 * </pre>
 * and all audit trails will automatically reflect the logged-in user.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // TODO: replace with SecurityContextHolder lookup once auth is added
        return () -> Optional.of("system");
    }
}
