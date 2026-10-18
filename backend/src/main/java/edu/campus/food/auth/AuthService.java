package edu.campus.food.auth;

import edu.campus.food.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService implements UserDetailsService {
    public static final Set<String> STAFF_ROLES = Set.of("ADMIN", "PRINCIPAL", "MANAGER", "INSPECTOR", "STAFF", "PURCHASER", "CHEF", "CLEANER");
    private final UserMapper users;
    private final PasswordEncoder encoder;

    public AuthService(UserMapper users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> user = users.byUsername(username);
        if (user == null) throw new UsernameNotFoundException("账号或密码不正确");
        long id = ((Number) user.get("id")).longValue();
        String[] authorities = users.roles(id).stream().map(role -> "ROLE_" + role).toArray(String[]::new);
        return User.withUsername(username)
                .password((String) user.get("password_hash"))
                .authorities(authorities)
                .disabled(!Boolean.TRUE.equals(user.get("active")))
                .build();
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String displayName, String identityType) {
        if (users.byUsername(username) != null) throw new ApiException(HttpStatus.CONFLICT, "用户名已被使用");
        if (!List.of("STUDENT", "TEACHER").contains(identityType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "身份类别只能为学生或教师");
        }
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("passwordHash", encoder.encode(password));
        user.put("displayName", displayName.trim());
        user.put("identityType", identityType);
        user.put("selfRegistered", true);
        users.insert(user);
        users.addRole(((Number) user.get("id")).longValue(), "COMMUNITY");
        return Map.of("id", user.get("id"), "username", username);
    }

    @Transactional
    public Map<String, Object> createStaff(String username, String password, String displayName, String role, List<Long> canteenIds) {
        if (!STAFF_ROLES.contains(role)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的角色");
        if (username == null || !username.matches("[A-Za-z0-9_]{4,32}")) throw new ApiException(HttpStatus.BAD_REQUEST, "账号需为4至32位字母、数字或下划线");
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "姓名不能为空且不能超过100字");
        if (users.byUsername(username) != null) throw new ApiException(HttpStatus.CONFLICT, "用户名已被使用");
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("passwordHash", encoder.encode(password));
        user.put("displayName", displayName.trim());
        user.put("identityType", null);
        user.put("selfRegistered", false);
        users.insert(user);
        long id = ((Number) user.get("id")).longValue();
        users.addRole(id, role);
        for (Long canteenId : canteenIds) users.addScope(id, canteenId);
        return users.byId(id);
    }

    @Transactional
    public void changePassword(long id, String oldPassword, String newPassword) {
        Map<String, Object> user = users.byId(id);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "账号不存在");
        Map<String, Object> withPassword = users.byUsername((String) user.get("username"));
        if (!encoder.matches(oldPassword, (String) withPassword.get("password_hash"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        users.setPassword(id, encoder.encode(newPassword));
    }

    public Map<String, Object> profile(String username) {
        Map<String, Object> user = users.byUsername(username);
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        long id = ((Number) user.get("id")).longValue();
        return Map.of(
                "id", id,
                "username", username,
                "displayName", user.get("display_name"),
                "roles", users.roles(id),
                "canteenIds", users.scopes(id)
        );
    }
}
