package edu.campus.food.algorithm;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic greedy planner: prioritize risk, then use the least-loaded feasible slot. */
public final class SchedulePlanner {
    private SchedulePlanner() {}

    public record Candidate(long canteenId, double priority, int daysSinceLast, String reason) {}
    public record Slot(LocalDate date, long inspectorId, int capacity) {}
    public record Assignment(Candidate candidate, Slot slot) {}

    public static List<Assignment> plan(List<Candidate> candidates, List<Slot> slots, Set<Long> alreadyScheduled) {
        List<Candidate> ordered = candidates.stream()
                .filter(candidate -> !alreadyScheduled.contains(candidate.canteenId()))
                .sorted(Comparator.comparingDouble(Candidate::priority).reversed()
                        .thenComparing(Comparator.comparingInt(Candidate::daysSinceLast).reversed())
                        .thenComparingLong(Candidate::canteenId))
                .toList();
        Map<Slot, Integer> assigned = new HashMap<>();
        List<Assignment> result = new ArrayList<>();
        for (Candidate candidate : ordered) {
            Slot choice = slots.stream()
                    .filter(slot -> slot.capacity() > assigned.getOrDefault(slot, 0))
                    .min(Comparator.comparingInt((Slot slot) -> assigned.getOrDefault(slot, 0))
                            .thenComparing(Slot::date).thenComparingLong(Slot::inspectorId))
                    .orElse(null);
            if (choice == null) continue;
            assigned.merge(choice, 1, Integer::sum);
            result.add(new Assignment(candidate, choice));
        }
        return result;
    }
}
