package com.university.registration.facade;

import java.util.List;

public record RegistrationSnapshot(Registration registration, String enrollmentRequest, String seat,
                                   String invoice, String transcriptEntry) {

    public List<String> lines() {
        return List.of(
                "Chain        " + registration,
                "A eligibility " + enrollmentRequest,
                "B seating     " + seat,
                "C invoice     " + invoice,
                "C transcript  " + transcriptEntry);
    }
}
