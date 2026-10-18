package edu.campus.food.auth;

import java.util.Collection;
import java.util.Set;

public final class WorkRoles {
    public static final Set<String> WORKERS = Set.of("STAFF", "PURCHASER", "CHEF", "CLEANER");
    public static final Set<String> SCOPED = Set.of("MANAGER", "STAFF", "PURCHASER", "CHEF", "CLEANER");

    private WorkRoles() {}

    public static boolean isWorker(Collection<String> roles) {
        return roles.stream().anyMatch(WORKERS::contains);
    }

    public static boolean canBeAssigned(Collection<String> roles) {
        return roles.contains("MANAGER") || isWorker(roles);
    }
}
