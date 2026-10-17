package com.university.registration.facade;

import com.university.registration.eligibility.EligibilityService;
import com.university.registration.records.BillingSystem;
import com.university.registration.records.TranscriptService;
import com.university.registration.seating.SeatingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ChainCompensator {

    private final RegistrationLog log;
    private final EligibilityService eligibility;
    private final SeatingService seating;
    private final BillingSystem billing;
    private final TranscriptService transcript;

    ChainCompensator(RegistrationLog log, EligibilityService eligibility, SeatingService seating,
                     BillingSystem billing, TranscriptService transcript) {
        this.log = log;
        this.eligibility = eligibility;
        this.seating = seating;
        this.billing = billing;
        this.transcript = transcript;
    }

    List<String> unwind(long registrationId, String failedStage, String reason) {
        Registration registration = log.find(registrationId)
                .orElseThrow(() -> new InvalidRegistrationException("No registration #" + registrationId));
        if (registration.status() != Registration.Status.COMPENSATING) {
            registration.startCompensation(failedStage, reason);
            log.save(registration);
        }
        String ref = registration.reference();
        List<String> undone = new ArrayList<>();
        transcript.removeFor(ref).ifPresent(entry ->
                undone.add("C: removed transcript entry #" + entry.id() + " " + entry.courseCode() + " " + entry.term()));
        billing.voidFor(ref).ifPresent(invoice ->
                undone.add(String.format(Locale.ROOT, "C: voided invoice #%d, %,d KZT returned to %s",
                        invoice.id(), invoice.amount(), invoice.studentId())));
        seating.release(ref).ifPresent(seat ->
                undone.add("B: released seat #" + seat.id() + " in " + seat.courseCode()));
        eligibility.cancel(ref, "Compensated: " + registration.reason()).ifPresent(request ->
                undone.add("A: cancelled enrollment request #" + request.id()));
        registration.moveTo(Registration.Status.COMPENSATED);
        log.save(registration);
        return undone;
    }
}
