package com.enterprise.bts.security;

import com.enterprise.bts.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Used by the login flow (DaoAuthenticationProvider). Emails are normalised exactly as stored. */
    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Blank username");
        }
        String normalised = username.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalised)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }

    /** Used on every cookie-authenticated request, so disabling an account takes effect immediately. */
    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> findById(UUID id) {
        return userRepository.findById(id).map(AppUserPrincipal::from);
    }
}
