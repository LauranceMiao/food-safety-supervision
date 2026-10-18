package edu.campus.food.common;

import edu.campus.food.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditMapper mapper;
    private final CurrentUser current;
    private final HttpServletRequest request;

    public AuditService(AuditMapper mapper, CurrentUser current, HttpServletRequest request) {
        this.mapper = mapper;
        this.current = current;
        this.request = request;
    }

    public void log(String action, String entityType, Long entityId, String detail) {
        mapper.insert(current.id(), action, entityType, entityId, detail, request.getRemoteAddr());
    }

    public void anonymous(String action, String entityType, Long entityId, String detail) {
        mapper.insert(null, action, entityType, entityId, detail, request.getRemoteAddr());
    }
}
