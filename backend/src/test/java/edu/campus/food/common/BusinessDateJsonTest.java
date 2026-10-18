package edu.campus.food.common;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessDateJsonTest {
    @Test
    void sqlDatesStayOnTheirBusinessDayAndDateTimesKeepTheirTime() {
        var builder = JsonMapper.builder();
        new BusinessDateJsonConfig().businessDates().customize(builder);
        var mapper = builder.build();
        var json = mapper.readTree(mapper.writeValueAsString(Map.of(
                "service_date", Date.valueOf("2026-09-26"),
                "created_at", LocalDateTime.of(2026, 9, 26, 18, 30))));
        assertEquals("2026-09-26", json.get("service_date").asString());
        assertEquals("2026-09-26T18:30:00", json.get("created_at").asString());
    }
}
