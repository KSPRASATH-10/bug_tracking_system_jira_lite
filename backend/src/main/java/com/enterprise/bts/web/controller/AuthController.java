package com.enterprise.bts.web.controller;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.security.AuthCookieService;
import com.enterprise.bts.service.AuthService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.dto.LoginRequest;
import com.enterprise.bts.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Cookie-based session management")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or not authenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Too many login attempts (see Retry-After)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in",
            description = "Verifies credentials and sets the HttpOnly session cookie. Rate limited per client address.")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        cookieService.writeAuthCookie(response, result.token());
        return result.user();
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out", description = "Clears the session cookie. Safe to call when already signed out.")
    public void logout(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal,
                       HttpServletResponse response) {
        authService.recordLogout(principal);
        cookieService.clearAuthCookie(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Returns the signed-in user; the SPA uses it to restore a session.")
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.me(principal);
    }
}
