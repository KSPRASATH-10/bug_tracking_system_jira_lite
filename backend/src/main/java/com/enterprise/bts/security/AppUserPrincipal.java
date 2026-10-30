package com.enterprise.bts.security;

import com.enterprise.bts.domain.SystemRole;
import com.enterprise.bts.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Immutable authenticated identity placed in the SecurityContext. Only the SYSTEM role is carried here;
 * project-scoped roles are always resolved from the database at decision time (see ProjectSecurity),
 * so a membership change takes effect on the very next request.
 */
public final class AppUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final SystemRole systemRole;
    private final boolean enabled;

    private AppUserPrincipal(UUID id, String email, String fullName, String passwordHash,
                             SystemRole systemRole, boolean enabled) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.systemRole = systemRole;
        this.enabled = enabled;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getFullName(),
                user.getPasswordHash(), user.getSystemRole(), user.isEnabled());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public boolean isAdministrator() {
        return systemRole == SystemRole.ADMINISTRATOR;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AppUserPrincipal other && Objects.equals(id, other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        // Never include the password hash.
        return "AppUserPrincipal[id=" + id + ", email=" + email + ", role=" + systemRole + "]";
    }
}
