package edu.campus.food.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import edu.campus.food.auth.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private final AuditMapper audit;
    private final UserMapper users;
    private final HttpServletRequest request;

    public ApiErrors(AuditMapper audit, UserMapper users, HttpServletRequest request) {
        this.audit = audit;
        this.users = users;
        this.request = request;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException ex) {
        if (ex.status() == HttpStatus.FORBIDDEN) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long actorId = null;
            if (authentication != null) {
                Map<String, Object> user = users.byUsername(authentication.getName());
                if (user != null) actorId = ((Number) user.get("id")).longValue();
            }
            audit.insert(actorId, "ACCESS_DENIED", "http_request", null,
                    request.getMethod() + " " + request.getRequestURI(), request.getRemoteAddr());
        }
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> validation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException invalid
                ? invalid.getBindingResult().getAllErrors().get(0).getDefaultMessage()
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message == null ? "请求参数不正确" : message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "记录重复或引用的数据不存在"));
    }
}
