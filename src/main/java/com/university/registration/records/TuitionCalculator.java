package com.university.registration.records;

public final class TuitionCalculator {

    public static final long RATE_PER_CREDIT = 25_000;

    public long tuitionFor(int credits, int scholarshipPercent) {
        if (credits <= 0 || scholarshipPercent < 0 || scholarshipPercent > 100) {
            throw new IllegalArgumentException("Invalid tuition input: " + credits + " credits, " + scholarshipPercent + "%");
        }
        long gross = credits * RATE_PER_CREDIT;
        return gross - gross * scholarshipPercent / 100;
    }

    public boolean fitsCreditLimit(StudentAccount account, long amount) {
        return account.balance() + amount <= account.creditLimit();
    }
}
