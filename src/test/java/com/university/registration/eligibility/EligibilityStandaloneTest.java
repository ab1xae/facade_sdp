package com.university.registration.eligibility;

import com.university.registration.eligibility.EnrollmentRequest.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityStandaloneTest {

    @TempDir
    Path dataDir;

    private EligibilityService eligibility;

    @BeforeEach
    void freshStore() {
        EligibilityRepository repository = new EligibilityRepository(dataDir);
        repository.saveStudent(new StudentRecord("S1", "Aliya", Set.of("CS101"), null));
        repository.saveStudent(new StudentRecord("S2", "Bekzat", Set.of("CS101"), "Unpaid library fine"));
        repository.addPrerequisite("CS201", "CS101");
        repository.addPrerequisite("CS301", "CS201");
        eligibility = serviceOn(dataDir);
    }

    private static EligibilityService serviceOn(Path dir) {
        return new EligibilityService(new EligibilityRepository(dir), new PrerequisiteChecker(), new FinancialHoldChecker());
    }

    @Test
    void eligibleStudentGetsApprovedRequestThatSurvivesRestart() {
        EnrollmentRequest request = eligibility.submit("REF-1", "S1", "CS201");

        EnrollmentRequest reloaded = serviceOn(dataDir).requestFor("REF-1").orElseThrow();

        assertEquals(Status.APPROVED, request.status());
        assertEquals(request.id(), reloaded.id());
        assertEquals(Status.APPROVED, reloaded.status());
    }

    @Test
    void financialHoldRejectsAndRecordsTheRejection() {
        EligibilityException error = assertThrows(EligibilityException.class,
                () -> eligibility.submit("REF-1", "S2", "CS201"));

        assertTrue(error.getMessage().contains("Unpaid library fine"));
        assertEquals(Status.REJECTED, eligibility.requestFor("REF-1").orElseThrow().status());
        assertTrue(eligibility.approvedRequest("REF-1").isEmpty());
    }

    @Test
    void missingPrerequisiteRejects() {
        EligibilityException error = assertThrows(EligibilityException.class,
                () -> eligibility.submit("REF-1", "S1", "CS301"));

        assertEquals("Missing prerequisites for CS301: CS201", error.getMessage());
    }

    @Test
    void completedOrDuplicateCourseRejects() {
        eligibility.submit("REF-1", "S1", "CS201");

        assertThrows(EligibilityException.class, () -> eligibility.submit("REF-2", "S1", "CS201"));
        assertThrows(EligibilityException.class, () -> eligibility.submit("REF-3", "S1", "CS101"));
        assertThrows(EligibilityException.class, () -> eligibility.submit("REF-4", "S9", "CS101"));
    }

    @Test
    void cancelIsIdempotentAndFreesTheCourseForANewRequest() {
        eligibility.submit("REF-1", "S1", "CS201");

        assertTrue(eligibility.cancel("REF-1", "test").isPresent());
        assertFalse(eligibility.cancel("REF-1", "again").isPresent());
        assertEquals(Status.CANCELLED, serviceOn(dataDir).requestFor("REF-1").orElseThrow().status());
        assertEquals(Status.APPROVED, eligibility.submit("REF-2", "S1", "CS201").status());
        assertEquals(2, eligibility.requests().size());
    }

    @Test
    void holdsCanBePlacedAndLifted() {
        eligibility.placeHold("S1", "Dormitory debt");
        assertThrows(EligibilityException.class, () -> eligibility.submit("REF-1", "S1", "CS201"));

        eligibility.liftHold("S1");

        assertEquals(Status.APPROVED, eligibility.submit("REF-2", "S1", "CS201").status());
        assertEquals(2, eligibility.students().size());
        assertTrue(eligibility.student("S2").orElseThrow().toString().contains("HOLD"));
    }

    @Test
    void requestEnforcesItsOwnLegalTransitions() {
        EnrollmentRequest request = EnrollmentRequest.open("REF-1", "S1", "CS201");

        assertThrows(IllegalStateException.class, () -> request.cancel("not approved yet"));
        request.approve();
        assertThrows(IllegalStateException.class, request::approve);
        request.cancel("done");
        assertThrows(IllegalStateException.class, () -> request.reject("too late"));
        assertTrue(request.toString().contains("CANCELLED"));
    }

    @Test
    void checkersAreIndependentBusinessRules() {
        PrerequisiteChecker checker = new PrerequisiteChecker();

        assertEquals(Set.of("CS201", "MATH101"), checker.missing(Set.of("CS101", "CS201", "MATH101"), Set.of("CS101")));
        assertTrue(new FinancialHoldChecker().blockingHold(new StudentRecord("S3", "Dana", Set.of(), " ")).isEmpty());
    }
}
