package com.manhpham.auth.services.impl;

import com.manhpham.auth.dto.LoginRequest;
import com.manhpham.auth.dto.RegisterRequest;
import com.manhpham.auth.dto.TokenResponse;
import com.manhpham.auth.entities.Role;
import com.manhpham.auth.entities.User;
import com.manhpham.auth.event.UserRegisteredEvent;
import com.manhpham.auth.event.UserRegisteredOutboxEvent;
import com.manhpham.auth.handle.OutboxEventSender;
import com.manhpham.auth.repositories.jpa.UserRepository;
import com.manhpham.auth.services.AuthService;
import com.manhpham.auth.services.JwtService;
import com.manhpham.auth.utils.exception.EmailAlreadyUsedException;
import com.manhpham.auth.utils.exception.InvalidCredentialsException;
import com.manhpham.auth.utils.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor // sinh constructor cho các field final -> Spring tự inject (1 constructor)
public class AuthServiceImpl implements AuthService {

    private final UserRepository users;
    private final OutboxEventSender outbox;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), Role.USER);
        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(email);
        }
        outbox.fire(UserRegisteredOutboxEvent.of(UserRegisteredEvent.of(saved.getId(), saved.getEmail())));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(User::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        JwtService.IssuedToken token = jwtService.issueAccessToken(user);
        return TokenResponse.bearer(token.token(), token.expiresInSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
