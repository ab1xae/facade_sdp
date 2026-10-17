package com.university.registration.records;

import java.util.Locale;

public record StudentAccount(String studentId, long balance, long creditLimit, int scholarshipPercent) {

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Account %-4s balance %,d / limit %,d KZT, scholarship %d%%",
                studentId, balance, creditLimit, scholarshipPercent);
    }
}
