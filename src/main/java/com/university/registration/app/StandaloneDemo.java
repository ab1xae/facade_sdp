package com.university.registration.app;

import com.university.registration.eligibility.EligibilityRepository;
import com.university.registration.eligibility.EligibilityService;
import com.university.registration.eligibility.FinancialHoldChecker;
import com.university.registration.eligibility.PrerequisiteChecker;
import com.university.registration.eligibility.StudentRecord;
import com.university.registration.records.BillingSystem;
import com.university.registration.records.RecordsRepository;
import com.university.registration.records.StudentAccount;
import com.university.registration.records.TranscriptService;
import com.university.registration.records.TuitionCalculator;
import com.university.registration.seating.CourseSection;
import com.university.registration.seating.SeatAllocator;
import com.university.registration.seating.SeatingRepository;
import com.university.registration.seating.SeatingService;
import com.university.registration.seating.WaitlistManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class StandaloneDemo {

    private StandaloneDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("standalone-subsystems");
        System.out.println("Each subsystem used on its own, no facade. Store: " + dir);
        eligibility(dir);
        seating(dir);
        records(dir);
    }

    private static void eligibility(Path dir) {
        EligibilityRepository repository = new EligibilityRepository(dir);
        repository.saveStudent(new StudentRecord("S1", "Aliya", Set.of("CS101"), null));
        repository.addPrerequisite("CS201", "CS101");
        EligibilityService service = new EligibilityService(repository, new PrerequisiteChecker(), new FinancialHoldChecker());
        System.out.println("A  " + service.submit("LOCAL-1", "S1", "CS201"));
        System.out.println("A  " + service.cancel("LOCAL-1", "standalone demo").orElseThrow());
    }

    private static void seating(Path dir) {
        SeatingRepository repository = new SeatingRepository(dir);
        repository.saveSection(new CourseSection("CS201", "Data Structures", 5, "2026-FALL", 1, 1));
        SeatingService service = new SeatingService(repository, new SeatAllocator(), new WaitlistManager(repository));
        System.out.println("B  " + service.reserve("LOCAL-1", "S1", "CS201"));
        try {
            service.reserve("LOCAL-2", "S2", "CS201");
        } catch (RuntimeException e) {
            System.out.println("B  " + e.getMessage());
        }
        System.out.println("B  " + service.release("LOCAL-1").orElseThrow());
    }

    private static void records(Path dir) {
        RecordsRepository repository = new RecordsRepository(dir);
        repository.saveAccount(new StudentAccount("S1", 0, 500_000, 0));
        repository.saveTerm("2026-FALL", true);
        BillingSystem billing = new BillingSystem(repository, new TuitionCalculator());
        TranscriptService transcript = new TranscriptService(repository);
        System.out.println("C  " + billing.issue("LOCAL-1", "S1", "CS201", 5));
        System.out.println("C  " + transcript.record("LOCAL-1", "S1", "CS201", "2026-FALL"));
        System.out.println("C  " + billing.account("S1").orElseThrow());
    }
}
