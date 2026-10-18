package edu.campus.food.algorithm;

import edu.campus.food.admin.AdminMapper;
import edu.campus.food.auth.CurrentUser;
import edu.campus.food.common.AuditService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskServiceTest {
    @Test
    void doesNotDuplicateSnapshotWhenJdbcReturnsDecimalInsteadOfDouble() {
        AlgorithmMapper db = mock(AlgorithmMapper.class);
        AdminMapper admin = mock(AdminMapper.class);
        when(admin.canteen(1)).thenReturn(Map.of("id", 1L));
        when(db.activeConfig()).thenReturn(Map.of("version", 1,
                "weights_json", "{\"issues\":0.3,\"overdue\":0.2,\"repeat\":0.15,\"certificate\":0.15,\"rejection\":0.1,\"feedback\":0.1}",
                "thresholds_json", "{\"medium\":30,\"high\":60}"));
        when(db.dailyCount(eq(1L), any(LocalDate.class))).thenReturn(1);
        AtomicReference<Map<String, Object>> latest = new AtomicReference<>();
        when(db.latestRisk(1)).thenAnswer(invocation -> latest.get());
        when(db.saveRisk(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> saved = invocation.getArgument(0);
            Map<String, Object> row = new HashMap<>();
            row.put("level", saved.get("level"));
            row.put("score", new BigDecimal("0.00"));
            row.put("config_version", saved.get("configVersion"));
            row.put("window_end", saved.get("windowEnd"));
            row.put("breakdown_json", saved.get("breakdown"));
            latest.set(row);
            return 1;
        });
        var risk = new RiskService(db, admin, new JsonMapper(), mock(CurrentUser.class), mock(AuditService.class));

        risk.refresh(1);
        risk.refresh(1);

        verify(db, times(1)).saveRisk(anyMap());
        assertEquals("LOW", latest.get().get("level"));
    }
}
