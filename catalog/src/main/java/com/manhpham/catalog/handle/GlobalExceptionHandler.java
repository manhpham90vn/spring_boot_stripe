package com.manhpham.catalog.handle;

import com.manhpham.catalog.utils.exception.CatalogConflictException;
import com.manhpham.catalog.utils.exception.EventNotFoundException;
import com.manhpham.catalog.utils.exception.TicketTypeNotFoundException;
import com.manhpham.catalog.utils.exception.VenueNotFoundException;
import com.manhpham.catalog.utils.response.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({VenueNotFoundException.class, EventNotFoundException.class,
            TicketTypeNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    /** Vi phạm ràng buộc trạng thái/nghiệp vụ (vd sửa/xóa sự kiện đã phát hành). */
    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<ApiError> handleConflict(CatalogConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

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
