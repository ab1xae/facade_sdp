package com.university.registration.records;

public record StudentAccount(String studentId, long balance, long creditLimit, int scholarshipPercent) {

    @Override
    public String toString() {
        return String.format("Account %-4s balance %,d / limit %,d KZT, scholarship %d%%",
                studentId, balance, creditLimit, scholarshipPercent);
    }
}
