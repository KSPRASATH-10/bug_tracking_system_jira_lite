package com.enterprise.bts.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cookie JWT -> SecurityContext. Deliberately NOT a Spring @Component: it is instantiated inside
 * SecurityConfig and lives only in the security filter chain (a @Component filter would also be
 * auto-registered as a plain servlet filter and run outside the chain). An invalid, expired or
 * tampered token simply leaves the request unauthenticated; the entry point then answers 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthCookieService cookieService;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(AuthCookieService cookieService, JwtService jwtService,
                                   UserDetailsServiceImpl userDetailsService) {
        this.cookieService = cookieService;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<String> token = cookieService.extractToken(request);
        if (token.isPresent()) {
            Optional<UUID> userId = jwtService.parseUserId(token.get());
            if (userId.isPresent()) {
                userDetailsService.findById(userId.get())
                        .filter(AppUserPrincipal::isEnabled)
                        .ifPresent(principal -> {
                            UsernamePasswordAuthenticationToken authentication =
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            principal, null, principal.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContext context = SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(authentication);
                            SecurityContextHolder.setContext(context);
                        });
            }
        }
        chain.doFilter(request, response);
    }
}
