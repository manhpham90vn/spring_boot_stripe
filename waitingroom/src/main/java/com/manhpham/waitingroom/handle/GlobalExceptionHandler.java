package com.manhpham.waitingroom.handle;

import com.manhpham.waitingroom.utils.exception.CaptchaFailedException;
import com.manhpham.common.core.response.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ánh xạ exception → mã HTTP đúng ngữ nghĩa, trả khuôn {@link ApiError} (giống các service khác). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** CAPTCHA sai → 400 (chống bot). */
    @ExceptionHandler(CaptchaFailedException.class)
    public ResponseEntity<ApiError> handleCaptcha(CaptchaFailedException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    /** Validation @Valid @RequestBody trong WebFlux ném WebExchangeBindException. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.validation(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Validation failed", fieldErrors));
    }
}
