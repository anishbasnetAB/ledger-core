package com.anish.banking.bank.auth.security;

import com.anish.banking.bank.common.ApiError;
import com.anish.banking.bank.common.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper, StringRedisTemplate redis,
                           @Value("${app.ratelimit.login.max-requests}") int loginMaxRequests,
                           @Value("${app.ratelimit.login.window-seconds}") long loginWindowSeconds,
                           @Value("${app.ratelimit.transfer.max-requests}") int transferMaxRequests,
                           @Value("${app.ratelimit.transfer.window-seconds}") long transferWindowSeconds) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.rateLimitFilter = new RateLimitFilter(redis, objectMapper,
                loginMaxRequests, Duration.ofSeconds(loginWindowSeconds),
                transferMaxRequests, Duration.ofSeconds(transferWindowSeconds));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService);

        http
                .csrf(csrf -> csrf.disable())      // stateless bearer-token API, no cookies to forge
                .cors(Customizer.withDefaults())   // reuses CorsConfig's WebMvcConfigurer rules
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        .requestMatchers("/api/admin/reconciliation/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI())))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // After JwtAuthFilter so an authenticated /api/transfers request is already
                // rate-limited by user identity, not just by IP (see RateLimitFilter#identity).
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    // Same ApiError shape GlobalExceptionHandler uses everywhere else — Spring Security's
    // filter chain runs before any @ControllerAdvice, so 401/403 need their own writer.
    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path, null);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
