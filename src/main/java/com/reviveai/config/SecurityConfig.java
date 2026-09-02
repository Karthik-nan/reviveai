package com.reviveai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // Disable CSRF because this is a stateless REST API
                .csrf(csrf -> csrf.disable())

                // Enable CORS for the React frontend
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))

                // No HTTP session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth

                        // Razorpay webhook
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/webhooks/razorpay"
                        ).permitAll()

                        // Dashboard
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/dashboard/**"
                        ).permitAll()

                        // Recovery cases
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/recovery-cases",
                                "/api/recovery-cases/**"
                        ).permitAll()

                        // Subscriptions
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/subscriptions",
                                "/api/subscriptions/**"
                        ).permitAll()

                        // Customers
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers",
                                "/api/customers/**"
                        ).permitAll()

                        // Recovery Policies
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/policies",
                                "/api/policies/**"
                        ).permitAll()

                        // System Settings
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/settings",
                                "/api/settings/**"
                        ).permitAll()

                        // Health check
                        .requestMatchers(
                                "/actuator/health"
                        ).permitAll()

                        // Allow Spring Boot error forwarding
                        .requestMatchers(
                                "/error"
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Disable default authentication mechanisms
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        // React/Vite frontend
        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5174"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
