package com.islamshariful.authservice.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * Every timestamp in the service comes from this bean, never from {@code Instant.now()} scattered through
     * the code. Token expiry, lockout windows and audit columns are all time-dependent behaviour, and a
     * test that cannot move the clock has to sleep — which is how suites become slow and flaky.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public org.springframework.data.auditing.DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> java.util.Optional.of(clock.instant());
    }
}
