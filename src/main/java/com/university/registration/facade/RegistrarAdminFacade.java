package com.university.registration.facade;

import com.university.registration.eligibility.EligibilityService;
import com.university.registration.records.BillingSystem;
import com.university.registration.records.Invoice;
import com.university.registration.records.TranscriptService;
import com.university.registration.seating.SeatingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RegistrarAdminFacade {

    private final RegistrationLog log;
    private final EligibilityService eligibility;
    private final SeatingService seating;
    private final BillingSystem billing;
    private final TranscriptService transcript;
    private final ChainCompensator compensator;

    public RegistrarAdminFacade(RegistrationLog log, EligibilityService eligibility, SeatingService seating,
                                BillingSystem billing, TranscriptService transcript) {
        this.log = log;
        this.eligibility = eligibility;
        this.seating = seating;
        this.billing = billing;
        this.transcript = transcript;
        this.compensator = new ChainCompensator(log, eligibility, seating, billing, transcript);
    }

    public List<Registration> registrations() {
        return log.all();
    }

    public List<Registration> interrupted() {
        return log.inFlight();
    }

    public RegistrationSnapshot inspect(long registrationId) {
        Registration registration = find(registrationId);
        String ref = registration.reference();
        return new RegistrationSnapshot(registration,
                describe(eligibility.requestFor(ref)),
                describe(seating.seatFor(ref)),
                describe(billing.invoiceFor(ref)),
                describe(transcript.entryFor(ref)));
    }

    public List<String> compensate(long registrationId, String reason) {
        Registration registration = find(registrationId);
        String stage = switch (registration.status()) {
            case REJECTED, COMPENSATED -> throw new InvalidRegistrationException(
                    "Nothing to compensate: " + registration.reference() + " is " + registration.status());
            case STARTED -> "A";
            case PENDING_B -> "B";
            case PENDING_C -> "C";
            case COMPLETED -> "ADMIN";
            case COMPENSATING -> registration.failedStage();
        };
        billing.invoiceFor(registration.reference())
                .filter(invoice -> invoice.status() == Invoice.Status.PAID)
                .ifPresent(invoice -> {
                    throw new RegistrationException("Invoice #" + invoice.id() + " of " + registration.reference()
                            + " is already paid; refund it before withdrawing");
                });
        return compensator.unwind(registrationId, stage, reason);
    }

    public List<String> finishInterruptedCompensations() {
        List<String> report = new ArrayList<>();
        for (Registration registration : log.inFlight()) {
            if (registration.status() == Registration.Status.COMPENSATING) {
                report.add(registration.reference() + " finished compensation");
                compensator.unwind(registration.id(), registration.failedStage(), registration.reason())
                        .forEach(step -> report.add("  " + step));
            }
        }
        return report;
    }

    public Invoice payInvoice(long registrationId) {
        return billing.pay(find(registrationId).reference());
    }

    public void setBillingOnline(boolean online) {
        billing.setOnline(online);
    }

    public boolean isBillingOnline() {
        return billing.isOnline();
    }

    public void setTermOpen(String term, boolean open) {
        if (open) {
            transcript.openTerm(term);
        } else {
            transcript.lockTerm(term);
        }
    }

    public boolean isTermOpen(String term) {
        return transcript.isOpen(term);
    }

    public void setSectionCapacity(String courseCode, int capacity) {
        seating.changeCapacity(courseCode, capacity);
    }

    public void placeFinancialHold(String studentId, String reason) {
        eligibility.placeHold(studentId, reason);
    }

    public void liftFinancialHold(String studentId) {
        eligibility.liftHold(studentId);
    }

    public List<String> stateReport() {
        List<String> report = new ArrayList<>();
        report.add("== Chain progress (" + RegistrationLog.FILE_NAME + ")");
        log.all().forEach(registration -> report.add("  " + registration));
        report.add("== A: eligibility (eligibility.db)");
        eligibility.requests().forEach(request -> report.add("  " + request));
        report.add("== B: seating (seating.db)");
        seating.sections().forEach(section -> report.add(String.format("  %-8s seats %d/%d, waitlist %d/%d",
                section.code(), seating.occupiedSeats(section.code()), section.capacity(),
                seating.waitlist(section.code()).size(), section.waitlistCapacity())));
        seating.reservations().forEach(seat -> report.add("  " + seat));
        report.add("== C: records (records.db)  billing " + (billing.isOnline() ? "ONLINE" : "OFFLINE")
                + ", terms " + transcript.terms());
        billing.accounts().forEach(account -> report.add("  " + account));
        billing.invoices().forEach(invoice -> report.add("  " + invoice));
        transcript.entries().forEach(entry -> report.add("  " + entry));
        return report;
    }

    private Registration find(long registrationId) {
        return log.find(registrationId)
                .orElseThrow(() -> new InvalidRegistrationException("No registration #" + registrationId));
    }

    private static String describe(Optional<?> value) {
        return value.map(Object::toString).orElse("none");
    }
}
