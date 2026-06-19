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

    /**
     * Đăng ký user mới. Cả thao tác này nằm trong MỘT transaction (@Transactional):
     * ghi user + ghi outbox event commit cùng lúc — hoặc cùng thành công, hoặc cùng
     * rollback. Nhờ vậy không bao giờ có user đã tạo nhưng event báo "đã đăng ký" bị mất.
     */
    @Override
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        // Kiểm tra sớm để báo lỗi thân thiện trong trường hợp thường gặp...
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), Role.USER);
        User saved;
        try {
            // ...nhưng vẫn phải bắt lỗi unique constraint: hai request cùng email chạy
            // song song có thể vượt qua existsByEmail rồi mới đụng nhau ở DB (race condition).
            // saveAndFlush để lỗi unique bật ra NGAY tại đây, không phải lúc commit.
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(email);
        }
        // Phát event "user đã đăng ký" qua OUTBOX (ghi cùng transaction, Debezium đẩy lên
        // Kafka sau) → Notification/service khác tiêu thụ mà không mất event. Xem OutboxEventSender.
        outbox.fire(UserRegisteredOutboxEvent.of(UserRegisteredEvent.of(saved.getId(), saved.getEmail())));
        return saved;
    }

    /**
     * Đăng nhập: chỉ ĐỌC nên readOnly = true. Cố tình KHÔNG phân biệt "email không tồn
     * tại" với "sai mật khẩu" — cả hai đều ném InvalidCredentialsException để không lộ
     * email nào đã đăng ký (chống user enumeration).
     */
    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(User::isEnabled) // tài khoản bị khoá cũng coi như không hợp lệ
                .orElseThrow(InvalidCredentialsException::new);

        // So khớp mật khẩu thô với hash đã lưu (BCrypt tự tách salt trong hash). Không bao
        // giờ giải mã hay so sánh chuỗi trực tiếp.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Hợp lệ → phát JWT ký số (xem JwtServiceImpl). Trả kèm thời hạn sống của token.
        JwtService.IssuedToken token = jwtService.issueAccessToken(user);
        return TokenResponse.bearer(token.token(), token.expiresInSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** Chuẩn hoá email về chữ thường + bỏ khoảng trắng để so khớp/lưu nhất quán (tránh trùng do hoa-thường). */
    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
