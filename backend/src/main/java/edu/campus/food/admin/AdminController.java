package edu.campus.food.admin;

import edu.campus.food.auth.AuthService;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.auth.UserMapper;
import edu.campus.food.auth.WorkRoles;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditMapper;
import edu.campus.food.common.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class AdminController {
    private final AdminMapper admin;
    private final UserMapper users;
    private final AuthService auth;
    private final CurrentUser current;
    private final AuditService audit;
    private final AuditMapper auditMapper;
    private final PasswordEncoder encoder;

    public AdminController(AdminMapper admin, UserMapper users, AuthService auth, CurrentUser current,
                           AuditService audit, AuditMapper auditMapper, PasswordEncoder encoder) {
        this.admin = admin;
        this.users = users;
        this.auth = auth;
        this.current = current;
        this.audit = audit;
        this.auditMapper = auditMapper;
        this.encoder = encoder;
    }

    @GetMapping("/api/canteens")
    public List<Map<String, Object>> canteens() {
        return admin.canteens();
    }

    @GetMapping("/api/stalls")
    public List<Map<String, Object>> stalls() {
        return admin.stalls();
    }

    @GetMapping("/api/check-templates")
    public List<Map<String, Object>> templates() {
        return admin.templates();
    }

    @GetMapping("/api/check-templates/{id}/items")
    public List<Map<String, Object>> templateItems(@PathVariable long id) {
        return admin.templateItems(id);
    }

    @GetMapping("/api/admin/users")
    public List<Map<String, Object>> users() {
        return users.all().stream().map(user -> {
            Map<String, Object> result = new HashMap<>(user);
            long id = ((Number) user.get("id")).longValue();
            result.put("roles", users.roles(id));
            result.put("canteenIds", users.scopes(id));
            return result;
        }).toList();
    }

    public record NewUser(@NotBlank String username, @NotBlank @Size(min = 8, max = 64) String password,
                          @NotBlank String displayName, @NotBlank String role, List<Long> canteenIds) {}

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addUser(@Valid @RequestBody NewUser request) {
        List<Long> scopes = request.canteenIds() == null ? List.of() : request.canteenIds();
        if (WorkRoles.SCOPED.contains(request.role()) && scopes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "食堂总管和工作人员至少需要一个食堂范围");
        }
        for (Long canteenId : scopes) if (canteenId == null || admin.canteen(canteenId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "食堂编号不存在");
        }
        Map<String, Object> created = auth.createStaff(request.username(), request.password(), request.displayName(),
                request.role(), scopes);
        audit.log("CREATE_USER", "app_user", ((Number) created.get("id")).longValue(), request.username());
        return created;
    }

    @PutMapping("/api/admin/users/{id}/active")
    public Map<String, Boolean> active(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        if (id == current.id() && Boolean.FALSE.equals(body.get("active"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能停用当前管理员账号");
        }
        if (users.setActive(id, Boolean.TRUE.equals(body.get("active"))) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "账号不存在");
        }
        audit.log("SET_USER_ACTIVE", "app_user", id, String.valueOf(body.get("active")));
        return Map.of("ok", true);
    }

    @PutMapping("/api/admin/users/{id}/password")
    public Map<String, Boolean> resetPassword(@PathVariable long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码需为8至64位");
        }
        if (users.setPassword(id, encoder.encode(password)) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "账号不存在");
        audit.log("RESET_PASSWORD", "app_user", id, "管理员重置密码");
        return Map.of("ok", true);
    }

    public record AccessRequest(List<String> roles, List<Long> canteenIds) {}

    @PutMapping("/api/admin/users/{id}/access")
    @Transactional
    public Map<String, Object> access(@PathVariable long id, @RequestBody AccessRequest request) {
        if (users.byId(id) == null) throw new ApiException(HttpStatus.NOT_FOUND, "账号不存在");
        Set<String> allowed = Set.of("ADMIN", "PRINCIPAL", "MANAGER", "INSPECTOR", "STAFF", "PURCHASER", "CHEF", "CLEANER", "COMMUNITY");
        if (request.roles() == null || request.roles().isEmpty() || !allowed.containsAll(request.roles())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "至少选择一个有效角色");
        }
        if (id == current.id() && !request.roles().contains("ADMIN")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能移除当前管理员自身的管理权限");
        }
        if (request.roles().stream().anyMatch(WorkRoles.SCOPED::contains)
                && (request.canteenIds() == null || request.canteenIds().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "食堂总管和工作人员至少需要一个食堂范围");
        }
        users.clearRoles(id);
        for (String role : request.roles().stream().distinct().toList()) users.addRole(id, role);
        users.clearScopes(id);
        if (request.canteenIds() != null) for (Long canteenId : request.canteenIds().stream().distinct().toList()) {
            if (canteenId == null || admin.canteen(canteenId) == null) throw new ApiException(HttpStatus.BAD_REQUEST, "食堂编号不存在");
            users.addScope(id, canteenId);
        }
        audit.log("CHANGE_USER_ACCESS", "app_user", id, "roles=" + request.roles() + ",canteens=" + request.canteenIds());
        return Map.of("id", id, "roles", users.roles(id), "canteenIds", users.scopes(id));
    }

    @GetMapping("/api/admin/audit")
    public List<Map<String, Object>> logs() {
        return auditMapper.recent(200);
    }

    @PostMapping("/api/admin/canteens")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addCanteen(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "食堂名称不能为空");
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        values.put("location", String.valueOf(body.getOrDefault("location", "")));
        admin.addCanteen(values);
        audit.log("CREATE_CANTEEN", "canteen", ((Number) values.get("id")).longValue(), name);
        return values;
    }

    @PutMapping("/api/admin/canteens/{id}")
    public Map<String, Object> updateCanteen(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> previous = admin.canteen(id);
        if (previous == null) throw new ApiException(HttpStatus.NOT_FOUND, "食堂不存在");
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank() || name.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "食堂名称不能为空且不能超过100字");
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("name", name);
        values.put("location", String.valueOf(body.getOrDefault("location", "")));
        values.put("active", !Boolean.FALSE.equals(body.get("active")));
        admin.updateCanteen(values);
        audit.log("UPDATE_CANTEEN", "canteen", id, "before=" + previous + ",after=" + values);
        return admin.canteen(id);
    }

    @PostMapping("/api/admin/stalls")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addStall(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank() || !(body.get("canteenId") instanceof Number)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择食堂并填写档口名称");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("name", name);
        values.put("canteenId", ((Number) body.get("canteenId")).longValue());
        if (admin.canteen((long) values.get("canteenId")) == null) throw new ApiException(HttpStatus.BAD_REQUEST, "食堂不存在");
        admin.addStall(values);
        audit.log("CREATE_STALL", "stall", ((Number) values.get("id")).longValue(), name);
        return values;
    }

    @PutMapping("/api/admin/stalls/{id}")
    public Map<String, Object> updateStall(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> previous = admin.stall(id);
        if (previous == null) throw new ApiException(HttpStatus.NOT_FOUND, "档口不存在");
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank() || name.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "档口名称不能为空且不能超过100字");
        Map<String, Object> values = Map.of("id", id, "name", name, "active", !Boolean.FALSE.equals(body.get("active")));
        admin.updateStall(values);
        audit.log("UPDATE_STALL", "stall", id, "before=" + previous + ",after=" + values);
        return admin.stall(id);
    }

    @PostMapping("/api/admin/check-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addTemplate(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isBlank() || name.length() > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "模板名称不正确");
        Map<String, Object> value = new HashMap<>();
        value.put("name", name);
        admin.addTemplate(value);
        audit.log("CREATE_CHECK_TEMPLATE", "check_template", ((Number) value.get("id")).longValue(), name);
        return value;
    }

    @PostMapping("/api/admin/check-templates/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addTemplateItem(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        if (title.isBlank() || title.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "项目名称不正确");
        Map<String, Object> value = new HashMap<>();
        value.put("templateId", id);
        value.put("title", title);
        value.put("sortOrder", body.get("sortOrder") instanceof Number number ? number.intValue() : 0);
        admin.addTemplateItem(value);
        audit.log("ADD_CHECK_ITEM", "check_item", ((Number) value.get("id")).longValue(), title);
        return value;
    }

    @PutMapping("/api/admin/check-templates/{id}/active")
    public Map<String, Boolean> templateActive(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        if (admin.setTemplateActive(id, Boolean.TRUE.equals(body.get("active"))) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "模板不存在");
        audit.log("SET_TEMPLATE_ACTIVE", "check_template", id, String.valueOf(body.get("active")));
        return Map.of("ok", true);
    }

    @PutMapping("/api/admin/check-items/{id}/active")
    public Map<String, Boolean> itemActive(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        if (admin.setTemplateItemActive(id, Boolean.TRUE.equals(body.get("active"))) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "项目不存在");
        audit.log("SET_CHECK_ITEM_ACTIVE", "check_item", id, String.valueOf(body.get("active")));
        return Map.of("ok", true);
    }
}
