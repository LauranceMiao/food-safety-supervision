package edu.campus.food.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuditService audit;
    private final ConcurrentHashMap<String, RegisterCount> registrationCounts = new ConcurrentHashMap<>();
    private record RegisterCount(Instant since, int count) {}

    public AuthController(AuthService auth, AuditService audit) {
        this.auth = auth;
        this.audit = audit;
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名需为4至32位字母、数字或下划线") String username,
            @NotBlank @Size(min = 8, max = 64, message = "密码需为8至64位") String password,
            @NotBlank String confirmPassword,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank String identityType) {}

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, max = 64) String newPassword) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        Instant now = Instant.now();
        if (registrationCounts.size() > 10_000) registrationCounts.entrySet().removeIf(entry -> entry.getValue().since().isBefore(now.minusSeconds(900)));
        RegisterCount attempts = registrationCounts.compute(servletRequest.getRemoteAddr(), (ip, old) ->
                old == null || old.since().isBefore(now.minusSeconds(900))
                        ? new RegisterCount(now, 1) : new RegisterCount(old.since(), old.count() + 1));
        if (attempts.count() > 10) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "注册操作过于频繁，请15分钟后再试");
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
        Map<String, Object> created = auth.register(request.username(), request.password(), request.displayName(), request.identityType());
        audit.anonymous("SELF_REGISTER", "app_user", ((Number) created.get("id")).longValue(), request.username());
        return created;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return auth.profile(authentication.getName());
    }

    @PostMapping("/password")
    public Map<String, Boolean> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        long id = ((Number) auth.profile(authentication.getName()).get("id")).longValue();
        auth.changePassword(id, request.oldPassword(), request.newPassword());
        return Map.of("ok", true);
    }
}
