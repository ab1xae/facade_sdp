package com.university.registration.facade;

import com.university.registration.eligibility.EligibilityException;
import com.university.registration.eligibility.EligibilityService;
import com.university.registration.records.BillingSystem;
import com.university.registration.records.FinalizationException;
import com.university.registration.records.TranscriptService;
import com.university.registration.seating.CourseSection;
import com.university.registration.seating.SeatUnavailableException;
import com.university.registration.seating.SeatingService;

import java.util.List;
import java.util.Locale;

import static com.university.registration.facade.Registration.Status.COMPLETED;
import static com.university.registration.facade.Registration.Status.PENDING_B;
import static com.university.registration.facade.Registration.Status.PENDING_C;
import static com.university.registration.facade.Registration.Status.STARTED;

public final class RegistrationFacade {

    private final RegistrationLog log;
    private final EligibilityService eligibility;
    private final SeatingService seating;
    private final BillingSystem billing;
    private final TranscriptService transcript;
    private final ChainCompensator compensator;
    private final StepListener listener;

    public RegistrationFacade(RegistrationLog log, EligibilityService eligibility, SeatingService seating,
                              BillingSystem billing, TranscriptService transcript, StepListener listener) {
        this.log = log;
        this.eligibility = eligibility;
        this.seating = seating;
        this.billing = billing;
        this.transcript = transcript;
        this.listener = listener;
        this.compensator = new ChainCompensator(log, eligibility, seating, billing, transcript);
    }

    public Registration register(String studentId, String courseCode) {
        String student = required(studentId, "Student id").toUpperCase(Locale.ROOT);
        String course = required(courseCode, "Course code").toUpperCase(Locale.ROOT);
        if (eligibility.student(student).isEmpty()) {
            throw new InvalidRegistrationException("Unknown student: " + student);
        }
        if (seating.section(course).isEmpty()) {
            throw new InvalidRegistrationException("Unknown course: " + course);
        }
        return drive(log.create(student, course));
    }

    public Registration resume(long registrationId) {
        Registration registration = log.find(registrationId)
                .orElseThrow(() -> new InvalidRegistrationException("No registration #" + registrationId));
        Registration.Status status = registration.status();
        if (status != STARTED && status != PENDING_B && status != PENDING_C) {
            throw new InvalidRegistrationException(registration.reference() + " is " + status + " and cannot be resumed");
        }
        return drive(registration);
    }

    private Registration drive(Registration registration) {
        String ref = registration.reference();
        if (registration.status() == STARTED) {
            if (eligibility.approvedRequest(ref).isEmpty()) {
                try {
                    eligibility.submit(ref, registration.studentId(), registration.courseCode());
                } catch (EligibilityException e) {
                    registration.reject(e.getMessage());
                    log.save(registration);
                    throw new RegistrationRejectedException(registration);
                }
            }
            advance(registration, PENDING_B);
        }
        if (registration.status() == PENDING_B) {
            if (seating.seatFor(ref).isEmpty()) {
                try {
                    seating.reserve(ref, registration.studentId(), registration.courseCode());
                } catch (SeatUnavailableException e) {
                    throw failed(registration, "B", e.getMessage());
                }
            }
            advance(registration, PENDING_C);
        }
        if (registration.status() == PENDING_C) {
            finalizeEnrollment(registration);
            seating.confirm(ref);
            advance(registration, COMPLETED);
        }
        return registration;
    }

    private void finalizeEnrollment(Registration registration) {
        String ref = registration.reference();
        CourseSection section = seating.section(registration.courseCode())
                .orElseThrow(() -> failed(registration, "C", "Section " + registration.courseCode() + " disappeared"));
        try {
            if (billing.invoiceFor(ref).isEmpty()) {
                billing.issue(ref, registration.studentId(), registration.courseCode(), section.credits());
            }
            if (transcript.entryFor(ref).isEmpty()) {
                transcript.record(ref, registration.studentId(), registration.courseCode(), section.term());
            }
        } catch (FinalizationException e) {
            throw failed(registration, "C", e.getMessage());
        }
    }

    private RegistrationFailedException failed(Registration registration, String stage, String reason) {
        List<String> undone = compensator.unwind(registration.id(), stage, reason);
        Registration persisted = log.find(registration.id()).orElseThrow();
        return new RegistrationFailedException(persisted, undone);
    }

    private void advance(Registration registration, Registration.Status next) {
        registration.moveTo(next);
        log.save(registration);
        listener.committed(registration);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidRegistrationException(label + " must not be empty");
        }
        return value.trim();
    }

    public List<String> students() {
        return eligibility.students().stream().map(Object::toString).toList();
    }

    public List<String> catalog() {
        return seating.sections().stream()
                .map(section -> String.format("%-8s %-28s %d cr  %s  seats %d/%d  waitlist %d/%d",
                        section.code(), section.title(), section.credits(), section.term(),
                        seating.occupiedSeats(section.code()), section.capacity(),
                        seating.waitlist(section.code()).size(), section.waitlistCapacity()))
                .toList();
    }
}
