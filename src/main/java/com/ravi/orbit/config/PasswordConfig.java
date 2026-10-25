package com.ravi.orbit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing is deliberately configured separately from the security
 * filter chain. AuthService needs this bean while SecurityConfig needs the
 * OAuth handlers, so keeping it here prevents a bean-creation cycle.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
