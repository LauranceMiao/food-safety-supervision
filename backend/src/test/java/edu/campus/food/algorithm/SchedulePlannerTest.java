package edu.campus.food.algorithm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchedulePlannerTest {
    @Test
    void prioritizesRiskAndNeverExceedsInspectorCapacity() {
        LocalDate monday = LocalDate.of(2026, 9, 28);
        var candidates = List.of(
                new SchedulePlanner.Candidate(1, 25, 30, "低风险"),
                new SchedulePlanner.Candidate(2, 85, 10, "高风险"),
                new SchedulePlanner.Candidate(3, 70, 20, "中风险"),
                new SchedulePlanner.Candidate(4, 95, 40, "已有任务"));
        var slots = List.of(
                new SchedulePlanner.Slot(monday, 11, 1),
                new SchedulePlanner.Slot(monday.plusDays(1), 12, 1));

        var plan = SchedulePlanner.plan(candidates, slots, Set.of(4L));

        assertEquals(List.of(2L, 3L), plan.stream().map(a -> a.candidate().canteenId()).toList());
        assertNotEquals(plan.get(0).slot(), plan.get(1).slot());
        assertTrue(plan.stream().allMatch(a -> a.slot().capacity() > 0));
    }

    @Test
    void deterministicTieBreakAndNoSlotMeansNoAssignment() {
        LocalDate monday = LocalDate.of(2026, 9, 28);
        var candidates = List.of(
                new SchedulePlanner.Candidate(3, 50, 20, ""),
                new SchedulePlanner.Candidate(2, 50, 20, ""));

        assertTrue(SchedulePlanner.plan(candidates, List.of(), Set.of()).isEmpty());
        var plan = SchedulePlanner.plan(candidates,
                List.of(new SchedulePlanner.Slot(monday, 7, 2)), Set.of());
        assertEquals(List.of(2L, 3L), plan.stream().map(a -> a.candidate().canteenId()).toList());
    }
}
