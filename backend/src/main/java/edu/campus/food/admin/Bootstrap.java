package edu.campus.food.admin;

import edu.campus.food.auth.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";

    @Bean
    ApplicationRunner initialAdmin(AdminMapper admin, UserMapper users, PasswordEncoder encoder,
                                   @Value("${app.bootstrap-admin-password:}") String configuredPassword) {
        return args -> {
            if (admin.userCount() > 0) return;
            String password = configuredPassword.isBlank() ? randomPassword() : configuredPassword;
            Map<String, Object> user = new HashMap<>();
            user.put("username", "admin");
            user.put("passwordHash", encoder.encode(password));
            user.put("displayName", "超级管理员");
            user.put("identityType", null);
            user.put("selfRegistered", false);
            users.insert(user);
            long id = ((Number) user.get("id")).longValue();
            users.addRole(id, "ADMIN");
            if (admin.configCount() == 0) {
                admin.insertDefaultConfig(
                        "{\"issues\":0.30,\"overdue\":0.20,\"repeat\":0.15,\"certificate\":0.15,\"rejection\":0.10,\"feedback\":0.10}",
                        "{\"medium\":30,\"high\":60}", id);
            }
            if (configuredPassword.isBlank()) log.warn("首次启动管理员账号：admin；随机初始密码：{}。请登录后立即修改。", password);
            else log.info("首次启动管理员账号：admin；使用 APP_BOOTSTRAP_ADMIN_PASSWORD 指定的密码。");
        };
    }

    private String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 18; i++) result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return result.toString();
    }
}
