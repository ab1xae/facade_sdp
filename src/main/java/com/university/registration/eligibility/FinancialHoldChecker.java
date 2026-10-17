package com.university.registration.eligibility;

import java.util.Optional;

public final class FinancialHoldChecker {

    public Optional<String> blockingHold(StudentRecord student) {
        String hold = student.financialHold();
        if (hold == null || hold.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("Financial hold on " + student.id() + ": " + hold);
    }
}
