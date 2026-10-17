package com.university.registration.facade;

import com.university.registration.facade.RegistrationWorld.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.university.registration.eligibility.EnrollmentRequest.Status.APPROVED;
import static com.university.registration.eligibility.EnrollmentRequest.Status.CANCELLED;
import static com.university.registration.eligibility.EnrollmentRequest.Status.REJECTED;
import static com.university.registration.facade.Registration.Status.COMPENSATED;
import static com.university.registration.facade.Registration.Status.COMPLETED;
import static com.university.registration.records.Invoice.Status.ISSUED;
import static com.university.registration.seating.SeatReservation.Status.CONFIRMED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationFacadeTest {

    @TempDir
    Path dataDir;

    private RegistrationWorld world;

    @BeforeEach
    void cleanWorld() {
        world = RegistrationWorld.freshIn(dataDir);
        world.givenCourse("CS101").withCredits(5);
        world.givenCourse("CS201").requiring("CS101").withCredits(5).withSeats(10);
        world.givenStudent("S1").whoCompleted("CS101").withCreditLimit(500_000);
    }

    @Test
    void eligibleStudentWithFreeSeatAndCreditIsFullyRegistered() {
        Outcome outcome = world.whenStudent("S1").registersFor("cs201");

        outcome.shouldEndAs(COMPLETED);
        world.thenOnDisk(outcome)
                .enrollmentRequestIs(APPROVED)
                .seatIs(CONFIRMED)
                .invoiceIs(ISSUED)
                .isOnTranscript();
        assertEquals(125_000, world.balanceOf("S1"));
    }

    @Test
    void invalidInputIsRefusedBeforeAnythingIsPersisted() {
        world.whenStudent("S404").registersFor("CS201").shouldBeRefusedAsInvalid();
        world.whenStudent("S1").registersFor("CS999").shouldBeRefusedAsInvalid();
        world.whenStudent(" ").registersFor("CS201").shouldBeRefusedAsInvalid();
        world.whenStudent("S1").registersFor(null).shouldBeRefusedAsInvalid();

        assertTrue(world.admin().registrations().isEmpty());
    }

    @Test
    void studentWithFinancialHoldIsRejectedAtStageAWithNothingToUndo() {
        world.givenStudent("S2").whoCompleted("CS101").withFinancialHold("Unpaid library fine");

        Outcome outcome = world.whenStudent("S2").registersFor("CS201");

        outcome.shouldEndAs(Registration.Status.REJECTED).afterFailingAt("A").withReasonContaining("library fine");
        world.thenOnDisk(outcome).enrollmentRequestIs(REJECTED).holdsNoSeat().hasNoOpenInvoice();
    }

    @Test
    void fullSectionAfterApprovalCompensatesEligibilityAlone() {
        world.givenCourse("CS301").requiring("CS101").withSeats(1).withWaitlist(2).alreadyTakenBy("S9");

        Outcome outcome = world.whenStudent("S1").registersFor("CS301");

        outcome.shouldEndAs(COMPENSATED).afterFailingAt("B").withReasonContaining("waitlist at position 1")
                .undoingInOrder("A");
        world.thenOnDisk(outcome)
                .enrollmentRequestIs(CANCELLED)
                .holdsNoSeat()
                .hasNoOpenInvoice()
                .isNotOnTranscript();
        assertEquals(0, world.balanceOf("S1"));
    }

    @Test
    void billingRejectionAfterSeatIsHeldCompensatesSeatingThenEligibility() {
        world.givenStudent("S3").whoCompleted("CS101").owing(380_000).withCreditLimit(400_000);

        Outcome outcome = world.whenStudent("S3").registersFor("CS201");

        outcome.shouldEndAs(COMPENSATED).afterFailingAt("C").withReasonContaining("exceeds credit limit")
                .undoingInOrder("B", "A");
        world.thenOnDisk(outcome)
                .enrollmentRequestIs(CANCELLED)
                .holdsNoSeat()
                .hasNoOpenInvoice();
        assertEquals(380_000, world.balanceOf("S3"));
        assertEquals(0, world.admin().stateReport().stream().filter(line -> line.contains("HELD")).count());
    }

    @Test
    void billingOutageAtStageCCompensatesSeatingThenEligibility() {
        world.givenBillingIsOffline();

        Outcome outcome = world.whenStudent("S1").registersFor("CS201");

        outcome.shouldEndAs(COMPENSATED).afterFailingAt("C").withReasonContaining("offline").undoingInOrder("B", "A");
        world.thenOnDisk(outcome).enrollmentRequestIs(CANCELLED).holdsNoSeat();
    }

    @Test
    void lockedTranscriptAfterInvoiceVoidsInvoiceThenReleasesSeatThenCancelsRequest() {
        world.givenTermIsLocked();

        Outcome outcome = world.whenStudent("S1").registersFor("CS201");

        outcome.shouldEndAs(COMPENSATED).afterFailingAt("C").withReasonContaining("locked")
                .undoingInOrder("C", "B", "A");
        world.thenOnDisk(outcome).enrollmentRequestIs(CANCELLED).holdsNoSeat().hasNoOpenInvoice().isNotOnTranscript();
        assertEquals(0, world.balanceOf("S1"));
    }

    @Test
    void compensatedStudentCanRegisterAgainOnceTheProblemIsFixed() {
        world.givenStudent("S3").whoCompleted("CS101").owing(380_000).withCreditLimit(400_000);
        world.whenStudent("S3").registersFor("CS201").shouldEndAs(COMPENSATED);

        world.givenStudent("S3").whoCompleted("CS101").owing(0).withCreditLimit(400_000);
        Outcome retry = world.whenStudent("S3").registersFor("CS201");

        retry.shouldEndAs(COMPLETED);
        assertEquals(2, world.admin().registrations().size());
    }

    @Test
    void secondRegistrationForTheSameCourseIsRejected() {
        world.whenStudent("S1").registersFor("CS201").shouldEndAs(COMPLETED);

        Outcome duplicate = world.whenStudent("S1").registersFor("CS201");

        duplicate.shouldEndAs(Registration.Status.REJECTED).withReasonContaining("already registered");
        assertTrue(duplicate.errorMessage().contains("rejected at stage A"));
    }

    @Test
    void catalogAndStudentListComeFromTheSubsystems() {
        world.whenStudent("S1").registersFor("CS201");

        assertTrue(world.registration().catalog().stream().anyMatch(line -> line.contains("CS201") && line.contains("1/10")));
        assertTrue(world.registration().students().get(0).contains("S1"));
    }

    @Test
    void chainStatusEnforcesItsOwnLegalTransitions() {
        Registration registration = new Registration(7, "S1", "CS201", Registration.Status.STARTED, null, null);

        registration.moveTo(Registration.Status.PENDING_B);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> registration.moveTo(Registration.Status.COMPLETED));
        assertTrue(registration.toString().startsWith("REG-7"));
        assertTrue(Registration.Status.COMPENSATING.inFlight());
    }
}
