package com.university.registration.facade;

import com.university.registration.app.Registrar;
import com.university.registration.eligibility.EligibilityRepository;
import com.university.registration.eligibility.EnrollmentRequest;
import com.university.registration.eligibility.StudentRecord;
import com.university.registration.records.Invoice;
import com.university.registration.records.RecordsRepository;
import com.university.registration.records.StudentAccount;
import com.university.registration.seating.CourseSection;
import com.university.registration.seating.SeatReservation;
import com.university.registration.seating.SeatingRepository;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class RegistrationWorld {

    static final String TERM = "2026-FALL";

    static final class SimulatedCrash extends RuntimeException {
        SimulatedCrash(Registration registration) {
            super("process killed after " + registration.reference() + " reached " + registration.status());
        }
    }

    private final Path dataDir;
    private Registration.Status crashAt;
    private Registrar registrar;

    private RegistrationWorld(Path dataDir) {
        this.dataDir = dataDir;
        new RecordsRepository(dataDir).saveTerm(TERM, true);
        restart();
    }

    static RegistrationWorld freshIn(Path dataDir) {
        return new RegistrationWorld(dataDir);
    }

    RegistrationWorld restart() {
        registrar = Registrar.open(dataDir, registration -> {
            if (registration.status() == crashAt) {
                crashAt = null;
                throw new SimulatedCrash(registration);
            }
        });
        return this;
    }

    RegistrationFacade registration() {
        return registrar.registration();
    }

    RegistrarAdminFacade admin() {
        return registrar.admin();
    }

    Path dataDir() {
        return dataDir;
    }

    StudentGiven givenStudent(String id) {
        return new StudentGiven(id);
    }

    CourseGiven givenCourse(String code) {
        return new CourseGiven(code);
    }

    RegistrationWorld givenBillingIsOffline() {
        admin().setBillingOnline(false);
        return this;
    }

    RegistrationWorld givenTermIsLocked() {
        admin().setTermOpen(TERM, false);
        return this;
    }

    RegistrationWorld crashingAfterReaching(Registration.Status status) {
        crashAt = status;
        return this;
    }

    Attempt whenStudent(String studentId) {
        return new Attempt(studentId);
    }

    Outcome whenResuming(Outcome previous) {
        try {
            return new Outcome(registration().resume(previous.registration().id()), List.of(), null);
        } catch (RegistrationFailedException e) {
            return new Outcome(e.registration(), e.compensations(), e);
        }
    }

    OnDisk thenOnDisk(Outcome outcome) {
        return new OnDisk(outcome.registration().reference());
    }

    long balanceOf(String studentId) {
        return new RecordsRepository(dataDir).findAccount(studentId).orElseThrow().balance();
    }

    Registration persisted(long registrationId) {
        return new RegistrationLog(dataDir).find(registrationId).orElseThrow();
    }

    final class StudentGiven {
        private final String id;
        private Set<String> completed = Set.of();
        private String hold;
        private long balance;
        private long creditLimit = 1_000_000;
        private int scholarship;

        private StudentGiven(String id) {
            this.id = id;
            save();
        }

        StudentGiven whoCompleted(String... courses) {
            completed = Set.of(courses);
            return save();
        }

        StudentGiven withFinancialHold(String reason) {
            hold = reason;
            return save();
        }

        StudentGiven owing(long amount) {
            balance = amount;
            return save();
        }

        StudentGiven withCreditLimit(long limit) {
            creditLimit = limit;
            return save();
        }

        StudentGiven withScholarship(int percent) {
            scholarship = percent;
            return save();
        }

        private StudentGiven save() {
            new EligibilityRepository(dataDir).saveStudent(new StudentRecord(id, "Student " + id, completed, hold));
            new RecordsRepository(dataDir).saveAccount(new StudentAccount(id, balance, creditLimit, scholarship));
            return this;
        }
    }

    final class CourseGiven {
        private final String code;
        private int credits = 5;
        private int seats = 30;
        private int waitlist = 5;

        private CourseGiven(String code) {
            this.code = code;
            save();
        }

        CourseGiven requiring(String... prerequisites) {
            EligibilityRepository repository = new EligibilityRepository(dataDir);
            Arrays.stream(prerequisites).forEach(prerequisite -> repository.addPrerequisite(code, prerequisite));
            return this;
        }

        CourseGiven withCredits(int value) {
            credits = value;
            return save();
        }

        CourseGiven withSeats(int value) {
            seats = value;
            return save();
        }

        CourseGiven withWaitlist(int value) {
            waitlist = value;
            return save();
        }

        CourseGiven alreadyTakenBy(String studentId) {
            SeatingRepository repository = new SeatingRepository(dataDir);
            SeatReservation seat = SeatReservation.hold("SEED-" + studentId, studentId, code);
            seat.confirm();
            repository.save(seat);
            return this;
        }

        private CourseGiven save() {
            new SeatingRepository(dataDir).saveSection(new CourseSection(code, "Course " + code, credits, TERM, seats, waitlist));
            return this;
        }
    }

    final class Attempt {
        private final String studentId;

        private Attempt(String studentId) {
            this.studentId = studentId;
        }

        Outcome registersFor(String courseCode) {
            try {
                return new Outcome(registration().register(studentId, courseCode), List.of(), null);
            } catch (RegistrationFailedException e) {
                return new Outcome(e.registration(), e.compensations(), e);
            } catch (RegistrationRejectedException e) {
                return new Outcome(e.registration(), List.of(), e);
            } catch (InvalidRegistrationException e) {
                return new Outcome(null, List.of(), e);
            } catch (SimulatedCrash e) {
                List<Registration> inFlight = new RegistrationLog(dataDir).inFlight();
                return new Outcome(inFlight.get(inFlight.size() - 1), List.of(), e);
            }
        }
    }

    final class Outcome {
        private final Registration registration;
        private final List<String> compensations;
        private final RuntimeException error;

        private Outcome(Registration registration, List<String> compensations, RuntimeException error) {
            this.registration = registration;
            this.compensations = compensations;
            this.error = error;
        }

        Registration registration() {
            assertNotNull(registration, "no registration was persisted");
            return persisted(registration.id());
        }

        Outcome shouldEndAs(Registration.Status status) {
            assertEquals(status, registration().status(), () -> "error: " + error);
            return this;
        }

        Outcome afterFailingAt(String stage) {
            assertEquals(stage, registration().failedStage());
            return this;
        }

        Outcome withReasonContaining(String text) {
            assertTrue(registration().reason().contains(text), registration().reason());
            return this;
        }

        Outcome undoingInOrder(String... stages) {
            List<String> actual = compensations.stream().map(step -> step.substring(0, 1)).toList();
            assertEquals(List.of(stages), actual, compensations::toString);
            return this;
        }

        Outcome shouldBeRefusedAsInvalid() {
            assertNull(registration);
            assertTrue(error instanceof InvalidRegistrationException, String.valueOf(error));
            return this;
        }

        Outcome crashed() {
            assertTrue(error instanceof SimulatedCrash, String.valueOf(error));
            return this;
        }

        String errorMessage() {
            return error == null ? fail("expected an error") : error.getMessage();
        }
    }

    final class OnDisk {
        private final String reference;

        private OnDisk(String reference) {
            this.reference = reference;
        }

        OnDisk enrollmentRequestIs(EnrollmentRequest.Status status) {
            assertEquals(status, new EligibilityRepository(dataDir).latestByReference(reference).orElseThrow().status());
            return this;
        }

        OnDisk hasNoEnrollmentRequest() {
            assertTrue(new EligibilityRepository(dataDir).latestByReference(reference).isEmpty());
            return this;
        }

        OnDisk seatIs(SeatReservation.Status status) {
            assertEquals(status, new SeatingRepository(dataDir).seatFor(reference).orElseThrow().status());
            return this;
        }

        OnDisk holdsNoSeat() {
            assertTrue(new SeatingRepository(dataDir).seatFor(reference).isEmpty(), "seat still held");
            return this;
        }

        OnDisk invoiceIs(Invoice.Status status) {
            assertEquals(status, new RecordsRepository(dataDir).invoiceFor(reference).orElseThrow().status());
            return this;
        }

        OnDisk hasNoOpenInvoice() {
            assertTrue(new RecordsRepository(dataDir).invoiceFor(reference).isEmpty(), "invoice still open");
            return this;
        }

        OnDisk isOnTranscript() {
            assertTrue(new RecordsRepository(dataDir).transcriptEntryFor(reference).isPresent());
            return this;
        }

        OnDisk isNotOnTranscript() {
            assertTrue(new RecordsRepository(dataDir).transcriptEntryFor(reference).isEmpty());
            return this;
        }
    }
}
