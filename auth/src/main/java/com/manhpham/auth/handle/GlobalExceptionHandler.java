package com.manhpham.auth.handle;

import com.manhpham.auth.utils.exception.EmailAlreadyUsedException;
import com.manhpham.auth.utils.exception.InvalidCredentialsException;
import com.manhpham.auth.utils.exception.UserNotFoundException;
import com.manhpham.common.core.response.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ xử lý exception TẬP TRUNG cho toàn bộ controller của Auth.
 *
 * <p>{@code @RestControllerAdvice}: Spring sẽ "chặn" exception ném ra từ bất kỳ controller
 * nào, tìm hàm {@code @ExceptionHandler} khớp kiểu rồi dùng giá trị trả về làm response.
 * Nhờ vậy controller/service chỉ việc ném exception nghiệp vụ, không phải lặp lại code
 * dựng HTTP status ở mọi nơi → tách bạch "logic" và "biểu diễn lỗi HTTP".
 *
 * <p>Mỗi exception nghiệp vụ được ánh xạ sang một mã HTTP đúng ngữ nghĩa và trả về cùng
 * một khuôn {@link ApiError} để client luôn nhận lỗi với cấu trúc nhất quán.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Email đã tồn tại → 409 Conflict (xung đột với trạng thái hiện có của tài nguyên).
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    // Sai email/mật khẩu hoặc tài khoản bị khóa → 401 Unauthorized. Thông điệp cố tình
    // mơ hồ (không nói rõ sai chỗ nào) để chống dò email (user enumeration).
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage()));
    }

    // Token hợp lệ nhưng user không còn tồn tại → 404 Not Found.
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    /**
     * Lỗi do {@code @Valid} bắt được (vd email sai định dạng, mật khẩu quá ngắn) → 400.
     * Gom từng field lỗi thành map {tên field → thông điệp} để client biết CHÍNH XÁC ô nào
     * sai. Dùng {@code putIfAbsent} để mỗi field chỉ giữ thông báo lỗi đầu tiên (gọn gàng).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.validation(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Validation failed", fieldErrors));
    }
}
