package com.university.registration.app;

import com.university.registration.eligibility.EligibilityRepository;
import com.university.registration.eligibility.StudentRecord;
import com.university.registration.facade.RegistrationLog;
import com.university.registration.records.RecordsRepository;
import com.university.registration.records.StudentAccount;
import com.university.registration.seating.CourseSection;
import com.university.registration.seating.SeatingRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class DemoData {

    static final String TERM = "2026-FALL";

    private DemoData() {
    }

    static void reset(Path dataDir) throws IOException {
        for (String file : List.of(EligibilityRepository.FILE_NAME, SeatingRepository.FILE_NAME,
                RecordsRepository.FILE_NAME, RegistrationLog.FILE_NAME)) {
            Files.deleteIfExists(dataDir.resolve(file));
        }
    }

    static boolean seedIfEmpty(Path dataDir) {
        EligibilityRepository eligibility = new EligibilityRepository(dataDir);
        if (!eligibility.students().isEmpty()) {
            return false;
        }
        SeatingRepository seating = new SeatingRepository(dataDir);
        RecordsRepository records = new RecordsRepository(dataDir);

        student(eligibility, records, "S1", "Aliya Nurlanova", Set.of("CS101", "CS201", "MATH101"), null, 0, 600_000, 0);
        student(eligibility, records, "S2", "Bekzat Omarov", Set.of("CS101"), "Unpaid library fine", 0, 600_000, 0);
        student(eligibility, records, "S3", "Dana Seitkali", Set.of("CS101", "MATH101"), null, 380_000, 400_000, 0);
        student(eligibility, records, "S4", "Timur Akhmetov", Set.of(), null, 0, 600_000, 0);
        student(eligibility, records, "S5", "Madina Ospanova", Set.of("CS101", "CS201", "MATH101"), null, 0, 600_000, 50);

        seating.saveSection(new CourseSection("CS101", "Intro to Programming", 5, TERM, 30, 5));
        seating.saveSection(new CourseSection("CS201", "Data Structures", 5, TERM, 25, 5));
        seating.saveSection(new CourseSection("CS301", "Algorithms", 5, TERM, 1, 2));
        seating.saveSection(new CourseSection("SE310", "Software Design Patterns", 6, TERM, 20, 5));
        seating.saveSection(new CourseSection("MATH201", "Linear Algebra", 4, TERM, 15, 3));
        seating.saveSection(new CourseSection("HIST100", "History of Kazakhstan", 3, TERM, 40, 0));

        eligibility.addPrerequisite("CS201", "CS101");
        eligibility.addPrerequisite("CS301", "CS201");
        eligibility.addPrerequisite("SE310", "CS201");
        eligibility.addPrerequisite("MATH201", "MATH101");

        records.saveTerm(TERM, true);

        Registrar.open(dataDir).registration().register("S5", "CS301");
        return true;
    }

    private static void student(EligibilityRepository eligibility, RecordsRepository records, String id, String name,
                                Set<String> completed, String hold, long balance, long limit, int scholarship) {
        eligibility.saveStudent(new StudentRecord(id, name, completed, hold));
        records.saveAccount(new StudentAccount(id, balance, limit, scholarship));
    }
}
