package edu.campus.food.auth;

import edu.campus.food.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CurrentUser {
    private final AuthService auth;

    public CurrentUser(AuthService auth) {
        this.auth = auth;
    }

    private Map<String, Object> profile() {
        return auth.profile(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public long id() {
        return ((Number) profile().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<String> roles() {
        return (List<String>) profile().get("roles");
    }

    @SuppressWarnings("unchecked")
    public List<Long> canteens() {
        return (List<Long>) profile().get("canteenIds");
    }

    public boolean has(String role) {
        return roles().contains(role);
    }

    public boolean isWorker() {
        return WorkRoles.isWorker(roles());
    }

    public boolean isSupervisor() {
        return has("ADMIN") || has("PRINCIPAL") || has("INSPECTOR") || has("MANAGER");
    }

    public void requireRole(String... allowed) {
        for (String role : allowed) if (has(role)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "没有操作权限");
    }

    public void requireCanteen(long canteenId) {
        if (has("ADMIN") || has("PRINCIPAL") || has("INSPECTOR")) return;
        if ((has("MANAGER") || isWorker()) && canteens().contains(canteenId)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "无权访问该食堂");
    }

    public List<Map<String, Object>> visibleCanteens(List<Map<String, Object>> rows) {
        if (has("ADMIN") || has("PRINCIPAL") || has("INSPECTOR")) return rows;
        List<Long> allowed = canteens();
        return rows.stream().filter(row -> {
            Object value = row.get("canteen_id");
            return value instanceof Number && allowed.contains(((Number) value).longValue());
        }).toList();
    }
}
