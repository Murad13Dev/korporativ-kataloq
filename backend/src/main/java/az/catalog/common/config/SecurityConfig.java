package az.catalog.common.config;

import az.catalog.common.dto.ApiResponses.ErrorResponse;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.OffsetDateTime;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12: ~250ms per hash on commodity hardware, well above the 2026 minimum.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Added as a raw writer rather than through the typed DSL, whose
                        // method name has moved between Spring Security 6.x minor releases.
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()")))

                .authorizeHttpRequests(auth -> auth
                        // --- infrastructure ---------------------------------
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // --- public storefront ------------------------------
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/public/contact-requests",
                                "/api/public/newsletter-subscriptions",
                                "/api/public/products/*/inquiry",
                                "/api/public/products/*/notify-stock").permitAll()
                        // Carts, checkout and chat work for guests: authorization is
                        // enforced per-resource (session token / ownership) in the services.
                        .requestMatchers("/api/public/cart/**").permitAll()
                        .requestMatchers("/api/public/checkout/**").permitAll()
                        .requestMatchers("/api/public/chat/**").permitAll()
                        .requestMatchers("/api/public/payments/callback/**", "/api/public/payments/webhook/**").permitAll()

                        // --- customer account -------------------------------
                        .requestMatchers("/api/customer/auth/login",
                                "/api/customer/auth/register",
                                "/api/customer/auth/refresh",
                                "/api/customer/auth/forgot-password",
                                "/api/customer/auth/reset-password").permitAll()
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")

                        // --- admin ------------------------------------------
                        .requestMatchers("/api/admin/auth/login",
                                "/api/admin/auth/refresh",
                                "/api/admin/auth/forgot-password",
                                "/api/admin/auth/reset-password").permitAll()
                        .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/**")
                        .hasAnyRole("SUPER_ADMIN", "ADMIN", "EDITOR", "SUPPORT")

                        .anyRequest().denyAll())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, ErrorCode.UNAUTHENTICATED, request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, ErrorCode.ACCESS_DENIED, request.getRequestURI())))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            ErrorCode code, String path) throws java.io.IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                OffsetDateTime.now(), code.getStatus().value(), code.name(),
                code.getDefaultMessage(), path, null));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                HttpHeaders.ACCEPT_LANGUAGE, "X-Requested-With", "X-Cart-Session", "X-Visitor-Token",
                "Idempotency-Key"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, "X-Total-Count"));
        // Required for the refresh-token cookie.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }
}
