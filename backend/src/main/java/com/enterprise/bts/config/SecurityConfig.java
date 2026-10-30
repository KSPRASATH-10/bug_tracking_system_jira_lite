package com.enterprise.bts.config;

import com.enterprise.bts.security.AuthCookieService;
import com.enterprise.bts.security.JwtAuthenticationFilter;
import com.enterprise.bts.security.JwtService;
import com.enterprise.bts.security.LoginRateLimitFilter;
import com.enterprise.bts.security.UserDetailsServiceImpl;
import com.enterprise.bts.service.AuditService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** BCrypt cost 12. Seeded demo hashes (pgcrypto bf, 12 rounds) verify against this encoder. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsServiceImpl userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // hideUserNotFoundExceptions stays true and unknown users still trigger a dummy BCrypt check,
        // so neither the error nor the response time reveals whether an email exists.
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BtsProperties properties,
                                                   JwtService jwtService,
                                                   AuthCookieService cookieService,
                                                   UserDetailsServiceImpl userDetailsService,
                                                   ObjectMapper objectMapper,
                                                   AuditService auditService) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(cookieService, jwtService, userDetailsService);
        LoginRateLimitFilter rateLimitFilter = new LoginRateLimitFilter(
                properties.rateLimit().loginCapacity(),
                properties.rateLimit().loginRefillPeriodSeconds(),
                objectMapper);
        OriginCheckFilter originFilter = new OriginCheckFilter(properties.security().corsAllowedOrigin(), objectMapper);

        http
                // CSRF defence: the auth cookie is SameSite=Strict, CORS is a single-origin allow-list,
                // and OriginCheckFilter rejects any state-changing request carrying a foreign Origin.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(request, response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            recordDenial(auditService, request);
                            writeError(request, response, objectMapper, HttpStatus.FORBIDDEN,
                                    "ACCESS_DENIED", "You do not have permission to perform this action");
                        }))
                .addFilterBefore(originFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static CorsConfigurationSource corsConfigurationSource(BtsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(properties.security().corsAllowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", CorrelationIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Content-Disposition", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void recordDenial(AuditService auditService, HttpServletRequest request) {
        String method = clean(request.getMethod(), 10);
        String path = clean(request.getRequestURI(), 200);
        log.warn("Access denied: {} {}", method, path);
        try {
            auditService.record("ACCESS_DENIED", "HTTP", null, Map.of("method", method, "path", path));
        } catch (RuntimeException e) {
            // Auditing must never turn a clean 403 into a 500.
            log.error("Could not write ACCESS_DENIED audit record", e);
        }
    }

    private static void writeError(HttpServletRequest request, HttpServletResponse response,
                                   ObjectMapper objectMapper, HttpStatus status,
                                   String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                CorrelationIdFilter.current(), request.getRequestURI(), null));
    }

    /** Strips control characters (log forging) and truncates untrusted text before it is logged or audited. */
    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String safe = value.replaceAll("[\\r\\n\\t]", "_");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    /**
     * Defence-in-depth CSRF control. Browsers always attach an Origin header to cross-site state-changing
     * requests; if one is present it must be this API's own origin or the configured SPA origin.
     * Non-browser clients (curl, server-to-server) send no Origin and are unaffected.
     */
    private static final class OriginCheckFilter extends OncePerRequestFilter {

        private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

        private final String allowedOrigin;
        private final ObjectMapper objectMapper;

        OriginCheckFilter(String allowedOrigin, ObjectMapper objectMapper) {
            this.allowedOrigin = allowedOrigin;
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin != null && UNSAFE_METHODS.contains(request.getMethod()) && !isTrusted(origin, request)) {
                log.warn("Rejected cross-origin {} {} (origin='{}')", clean(request.getMethod(), 10),
                        clean(request.getRequestURI(), 200), clean(origin, 100));
                writeError(request, response, objectMapper, HttpStatus.FORBIDDEN,
                        "ORIGIN_NOT_ALLOWED", "Cross-origin request rejected");
                return;
            }
            chain.doFilter(request, response);
        }

        private boolean isTrusted(String origin, HttpServletRequest request) {
            return origin.equalsIgnoreCase(allowedOrigin) || origin.equalsIgnoreCase(ownOrigin(request));
        }

        private static String ownOrigin(HttpServletRequest request) {
            String scheme = request.getScheme();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
            return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        }
    }
}
