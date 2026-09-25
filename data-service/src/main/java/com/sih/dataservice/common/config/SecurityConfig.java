package com.sih.dataservice.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.jwt.JwtAuthenticationFilter;
import com.sih.dataservice.common.dto.ErrorResponse;
import com.sih.dataservice.common.filter.MDCFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow deployed frontend and local dev
        configuration.setAllowedOriginPatterns(List.of(
            "https://sihweb-production.up.railway.app",
            "https://*.vercel.app", // standard Vercel deployments
            "http://localhost:5173",
            "http://localhost:5174"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Explicitly including X-Request-Id from MDCFilter
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String traceId = MDC.get(MDCFilter.TRACE_ID_MDC_KEY);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ErrorResponse errorResponse = new ErrorResponse(
                                    "UNAUTHORIZED",
                                    "Authentication required: " + authException.getMessage(),
                                    traceId
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String traceId = MDC.get(MDCFilter.TRACE_ID_MDC_KEY);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ErrorResponse errorResponse = new ErrorResponse(
                                    "FORBIDDEN",
                                    "Access denied: " + accessDeniedException.getMessage(),
                                    traceId
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/health",
                                "/ping",
                                "/auth/**",
                                "/entities/**",
                                "/geo/**",
                                "/whatsapp/**",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/complaints/**").hasAnyRole("COMPLAINANT", "CYBER_OFFICER", "POLICE")
                        .requestMatchers("/incidents/**").hasAnyRole("CYBER_OFFICER", "POLICE", "BANK_EMPLOYEE", "BANK_MANAGER")
                        .requestMatchers("/bank-uploads/**").hasAnyRole("BANK_EMPLOYEE", "BANK_MANAGER", "CYBER_OFFICER")
                        .requestMatchers("/freeze-requests/**").hasAnyRole("POLICE", "CYBER_OFFICER", "BANK_EMPLOYEE", "BANK_MANAGER", "ADMIN")
                        .requestMatchers("/graph/**").hasAnyRole("CYBER_OFFICER", "POLICE", "BANK_EMPLOYEE", "BANK_MANAGER")
                        .requestMatchers("/policy/**").hasAnyRole("CYBER_OFFICER", "POLICE")
                        .requestMatchers("/stats/**").hasAnyRole("CYBER_OFFICER", "POLICE", "ADMIN")
                        .requestMatchers("/ml-ops/**").hasAnyRole("CYBER_OFFICER", "ADMIN", "POLICE")
                        .requestMatchers("/streaming/**").hasAnyRole("CYBER_OFFICER", "POLICE", "ADMIN")
                        .requestMatchers("/benchmarks/**").hasAnyRole("CYBER_OFFICER", "ADMIN", "POLICE")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
