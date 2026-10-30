package com.enterprise.bts.service;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.security.JwtService;
import com.enterprise.bts.web.dto.LoginRequest;
import com.enterprise.bts.web.dto.UserResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** The controller turns {@code token} into the HttpOnly cookie; the token is never put in a response body. */
    public record LoginResult(UserResponse user, String token) {
    }

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResult login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            // The attempted email is deliberately NOT audited: users sometimes paste a password into that field.
            auditService.recordAs(null, "LOGIN_FAILED", "USER", null,
                    Map.of("reason", e.getClass().getSimpleName()));
            // Same message for unknown user, wrong password and disabled account: no account enumeration.
            throw new BadCredentialsException("Invalid email or password");
        }
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        auditService.recordAs(principal.getId(), "LOGIN_SUCCEEDED", "USER", principal.getId(), Map.of());
        return new LoginResult(me(principal), jwtService.issueToken(principal.getId()));
    }

    public UserResponse me(AppUserPrincipal principal) {
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getFullName(),
                principal.getSystemRole());
    }

    /** Logout is stateless (the cookie is cleared by the controller); this only leaves an audit trail. */
    public void recordLogout(AppUserPrincipal principalOrNull) {
        if (principalOrNull != null) {
            auditService.recordAs(principalOrNull.getId(), "LOGOUT", "USER", principalOrNull.getId(), Map.of());
        }
    }
}
