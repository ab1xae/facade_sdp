package com.university.registration.facade;

import com.university.registration.facade.RegistrationWorld.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.university.registration.eligibility.EnrollmentRequest.Status.CANCELLED;
import static com.university.registration.facade.Registration.Status.COMPENSATED;
import static com.university.registration.facade.Registration.Status.COMPLETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrarAdminFacadeTest {

    @TempDir
    Path dataDir;

    private RegistrationWorld world;
    private RegistrarAdminFacade admin;

    @BeforeEach
    void cleanWorld() {
        world = RegistrationWorld.freshIn(dataDir);
        world.givenCourse("CS101").withCredits(4);
        world.givenStudent("S1");
        admin = world.admin();
    }

    @Test
    void inspectShowsWhatEachSubsystemPersistedForTheRegistration() {
        Outcome outcome = world.whenStudent("S1").registersFor("CS101");

        List<String> lines = admin.inspect(outcome.registration().id()).lines();

        assertEquals(5, lines.size());
        assertTrue(lines.get(0).contains("COMPLETED"));
        assertTrue(lines.get(1).contains("APPROVED"));
        assertTrue(lines.get(2).contains("CONFIRMED"));
        assertTrue(lines.get(3).contains("100,000 KZT ISSUED"));
        assertTrue(lines.get(4).contains("REGISTERED"));
        assertThrows(InvalidRegistrationException.class, () -> admin.inspect(42));
    }

    @Test
    void manualWithdrawalOfACompletedRegistrationUnwindsCThenBThenA() {
        Outcome outcome = world.whenStudent("S1").registersFor("CS101");

        List<String> undone = admin.compensate(outcome.registration().id(), "Student withdrew");

        assertEquals(List.of("C", "C", "B", "A"), undone.stream().map(step -> step.substring(0, 1)).toList());
        outcome.shouldEndAs(COMPENSATED).afterFailingAt("ADMIN");
        world.thenOnDisk(outcome).enrollmentRequestIs(CANCELLED).holdsNoSeat().hasNoOpenInvoice().isNotOnTranscript();
        assertEquals(0, world.balanceOf("S1"));
    }

    @Test
    void paidInvoiceBlocksWithdrawal() {
        Outcome outcome = world.whenStudent("S1").registersFor("CS101");
        admin.payInvoice(outcome.registration().id());

        RegistrationException error = assertThrows(RegistrationException.class,
                () -> admin.compensate(outcome.registration().id(), "too late"));

        assertTrue(error.getMessage().contains("already paid"));
        outcome.shouldEndAs(COMPLETED);
    }

    @Test
    void terminalRegistrationsHaveNothingToCompensate() {
        world.givenStudent("S2").withFinancialHold("Dormitory debt");
        Outcome rejected = world.whenStudent("S2").registersFor("CS101");
        Outcome completed = world.whenStudent("S1").registersFor("CS101");
        admin.compensate(completed.registration().id(), "withdrew");

        assertThrows(InvalidRegistrationException.class, () -> admin.compensate(rejected.registration().id(), "x"));
        assertThrows(InvalidRegistrationException.class, () -> admin.compensate(completed.registration().id(), "x"));
        assertTrue(admin.finishInterruptedCompensations().isEmpty());
    }

    @Test
    void failureSwitchesArePersistedAndDriveTheChain() {
        admin.setBillingOnline(false);
        admin.setTermOpen(RegistrationWorld.TERM, false);
        admin.setSectionCapacity("CS101", 0);
        admin.placeFinancialHold("S1", "Test hold");

        world.restart();
        RegistrarAdminFacade reopened = world.admin();

        assertFalse(reopened.isBillingOnline());
        assertFalse(reopened.isTermOpen(RegistrationWorld.TERM));
        world.whenStudent("S1").registersFor("CS101").withReasonContaining("Test hold");

        reopened.liftFinancialHold("S1");
        reopened.setBillingOnline(true);
        reopened.setTermOpen(RegistrationWorld.TERM, true);
        world.whenStudent("S1").registersFor("CS101").afterFailingAt("B").withReasonContaining("waitlist");
        reopened.setSectionCapacity("CS101", 5);
        world.whenStudent("S1").registersFor("CS101").shouldEndAs(COMPLETED);
    }

    @Test
    void stateReportCoversAllFourStores() {
        world.whenStudent("S1").registersFor("CS101");

        String report = String.join("\n", admin.stateReport());

        assertTrue(report.contains("registrations.db"));
        assertTrue(report.contains("eligibility.db"));
        assertTrue(report.contains("seating.db"));
        assertTrue(report.contains("records.db"));
        assertTrue(report.contains("Invoice #1"));
        assertTrue(report.contains("Transcript #1"));
    }
}
