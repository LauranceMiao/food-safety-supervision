package edu.campus.food.auth;

import tools.jackson.databind.json.JsonMapper;
import edu.campus.food.common.AuditMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, UserMapper userMapper,
                                            AuditMapper audit, JsonMapper json) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/csrf", "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(), Map.of("ok", true, "username", authentication.getName()));
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(), Map.of("error", "账号或密码不正确"));
                        }))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            json.writeValue(response.getWriter(), Map.of("ok", true));
                        }))
                .addFilterBefore(activeSessionFilter(userMapper, json), AuthorizationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(), Map.of("error", "请先登录"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                            Map<String, Object> account = authentication == null ? null : userMapper.byUsername(authentication.getName());
                            Long actorId = account == null ? null : ((Number) account.get("id")).longValue();
                            audit.insert(actorId, "ACCESS_DENIED", "http_request", null,
                                    request.getMethod() + " " + request.getRequestURI(), request.getRemoteAddr());
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            json.writeValue(response.getWriter(), Map.of("error", "没有操作权限"));
                        }));
        return http.build();
    }

    private OncePerRequestFilter activeSessionFilter(UserMapper users, JsonMapper json) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (request.getRequestURI().startsWith("/api/") && authentication != null
                        && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
                    Map<String, Object> account = users.byUsername(authentication.getName());
                    Set<String> sessionRoles = authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .filter(authority -> authority.startsWith("ROLE_"))
                            .collect(Collectors.toSet());
                    Set<String> liveRoles = account == null ? Set.of() : users.roles(((Number) account.get("id")).longValue())
                            .stream().map(role -> "ROLE_" + role).collect(Collectors.toSet());
                    if (account == null || !Boolean.TRUE.equals(account.get("active")) || !sessionRoles.equals(liveRoles)) {
                        SecurityContextHolder.clearContext();
                        if (request.getSession(false) != null) request.getSession(false).invalidate();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        json.writeValue(response.getWriter(), Map.of("error", "账号状态或权限已变更，请重新登录"));
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }
}
