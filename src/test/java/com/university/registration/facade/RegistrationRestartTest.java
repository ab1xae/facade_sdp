package com.university.registration.facade;

import com.university.registration.eligibility.EligibilityRepository;
import com.university.registration.eligibility.EligibilityService;
import com.university.registration.eligibility.FinancialHoldChecker;
import com.university.registration.eligibility.PrerequisiteChecker;
import com.university.registration.facade.RegistrationWorld.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.university.registration.eligibility.EnrollmentRequest.Status.APPROVED;
import static com.university.registration.eligibility.EnrollmentRequest.Status.CANCELLED;
import static com.university.registration.facade.Registration.Status.COMPENSATED;
import static com.university.registration.facade.Registration.Status.COMPLETED;
import static com.university.registration.facade.Registration.Status.PENDING_B;
import static com.university.registration.facade.Registration.Status.PENDING_C;
import static com.university.registration.seating.SeatReservation.Status.CONFIRMED;
import static com.university.registration.seating.SeatReservation.Status.HELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationRestartTest {

    @TempDir
    Path dataDir;

    private RegistrationWorld world;

    @BeforeEach
    void cleanWorld() {
        world = RegistrationWorld.freshIn(dataDir);
        world.givenCourse("CS101").withCredits(5);
        world.givenCourse("CS201").requiring("CS101").withCredits(5);
        world.givenStudent("S1").whoCompleted("CS101");
    }

    @Test
    void crashAfterSeatingLeavesPendingCOnDiskAndResumeFinishesAfterRestart() {
        Outcome crashed = world.crashingAfterReaching(PENDING_C).whenStudent("S1").registersFor("CS201");
        crashed.crashed();

        world.restart();

        assertEquals(List.of(crashed.registration().reference()),
                world.admin().interrupted().stream().map(Registration::reference).toList());
        crashed.shouldEndAs(PENDING_C);
        world.thenOnDisk(crashed).enrollmentRequestIs(APPROVED).seatIs(HELD).hasNoOpenInvoice();

        Outcome resumed = world.whenResuming(crashed);

        resumed.shouldEndAs(COMPLETED);
        world.thenOnDisk(resumed).seatIs(CONFIRMED).isOnTranscript();
        assertTrue(world.admin().interrupted().isEmpty());
    }

    @Test
    void crashAfterEligibilityIsUnwoundByAdminAfterRestart() {
        Outcome crashed = world.crashingAfterReaching(PENDING_B).whenStudent("S1").registersFor("CS201");

        world.restart();
        crashed.shouldEndAs(PENDING_B);
        List<String> undone = world.admin().compensate(crashed.registration().id(), "Abandoned after crash");

        assertEquals(1, undone.size());
        assertTrue(undone.get(0).startsWith("A:"));
        crashed.shouldEndAs(COMPENSATED).afterFailingAt("B");
        world.thenOnDisk(crashed).enrollmentRequestIs(CANCELLED).holdsNoSeat();
    }

    @Test
    void resumeTrustsWhatTheSubsystemPersistedEvenIfTheChainLogLagsBehind() {
        RegistrationLog log = new RegistrationLog(dataDir);
        Registration started = log.create("S1", "CS201");
        new EligibilityService(new EligibilityRepository(dataDir), new PrerequisiteChecker(), new FinancialHoldChecker())
                .submit(started.reference(), "S1", "CS201");

        world.restart();
        Registration resumed = world.registration().resume(started.id());

        assertEquals(COMPLETED, resumed.status());
        assertEquals(1, new EligibilityRepository(dataDir).requests().size());
    }

    @Test
    void compensationInterruptedMidwayIsFinishedAfterRestart() {
        Outcome crashed = world.crashingAfterReaching(PENDING_C).whenStudent("S1").registersFor("CS201");
        RegistrationLog log = new RegistrationLog(dataDir);
        Registration registration = log.find(crashed.registration().id()).orElseThrow();
        registration.startCompensation("C", "Power loss during compensation");
        log.save(registration);

        world.restart();
        List<String> report = world.admin().finishInterruptedCompensations();

        assertEquals(3, report.size());
        assertTrue(report.get(1).contains("B: released seat"));
        assertTrue(report.get(2).contains("A: cancelled"));
        crashed.shouldEndAs(COMPENSATED).withReasonContaining("Power loss");
        world.thenOnDisk(crashed).enrollmentRequestIs(CANCELLED).holdsNoSeat();
    }

    @Test
    void finishedOrUnknownRegistrationsCannotBeResumed() {
        Outcome done = world.whenStudent("S1").registersFor("CS201");

        assertThrows(InvalidRegistrationException.class, () -> world.registration().resume(done.registration().id()));
        assertThrows(InvalidRegistrationException.class, () -> world.registration().resume(999));
    }
}
