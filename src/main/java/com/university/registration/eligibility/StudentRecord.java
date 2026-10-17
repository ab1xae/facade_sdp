package com.university.registration.eligibility;

import java.util.Set;
import java.util.TreeSet;

public record StudentRecord(String id, String name, Set<String> completedCourses, String financialHold) {

    public StudentRecord {
        completedCourses = Set.copyOf(completedCourses);
    }

    @Override
    public String toString() {
        String hold = financialHold == null ? "" : "  HOLD: " + financialHold;
        return String.format("%-4s %-18s completed %s%s", id, name, new TreeSet<>(completedCourses), hold);
    }
}
