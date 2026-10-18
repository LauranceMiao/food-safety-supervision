package edu.campus.food.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceServiceTest {
    @Test
    void traversesBranchesAndStopsAtCycles() {
        var edges = List.<Map<String, Object>>of(
                Map.of("from", "batch", "to", "usage-1"),
                Map.of("from", "batch", "to", "usage-2"),
                Map.of("from", "usage-1", "to", "menu-1"),
                Map.of("from", "usage-2", "to", "menu-2"),
                Map.of("from", "menu-1", "to", "batch"),
                Map.of("from", "other-batch", "to", "other-menu"));

        assertEquals(Set.of("batch", "usage-1", "usage-2", "menu-1", "menu-2"),
                TraceService.traverse("batch", edges));
    }
}
