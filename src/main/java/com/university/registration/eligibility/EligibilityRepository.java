package com.university.registration.eligibility;

import com.university.registration.storage.SqliteStore;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class EligibilityRepository {

    public static final String FILE_NAME = "eligibility.db";

    private final SqliteStore store;

    public EligibilityRepository(Path dataDir) {
        store = new SqliteStore(dataDir.resolve(FILE_NAME),
                "CREATE TABLE IF NOT EXISTS students (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                        + "completed TEXT NOT NULL, financial_hold TEXT)",
                "CREATE TABLE IF NOT EXISTS prerequisites (course_code TEXT NOT NULL, prerequisite TEXT NOT NULL, "
                        + "PRIMARY KEY (course_code, prerequisite))",
                "CREATE TABLE IF NOT EXISTS enrollment_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "reference TEXT NOT NULL, student_id TEXT NOT NULL, course_code TEXT NOT NULL, "
                        + "status TEXT NOT NULL, note TEXT)");
    }

    public void saveStudent(StudentRecord student) {
        store.execute("INSERT OR REPLACE INTO students (id, name, completed, financial_hold) VALUES (?, ?, ?, ?)",
                student.id(), student.name(), String.join(",", new TreeSet<>(student.completedCourses())),
                student.financialHold());
    }

    public Optional<StudentRecord> findStudent(String id) {
        return store.first("SELECT * FROM students WHERE id = ?", EligibilityRepository::student, id);
    }

    public List<StudentRecord> students() {
        return store.list("SELECT * FROM students ORDER BY id", EligibilityRepository::student);
    }

    public void updateFinancialHold(String studentId, String hold) {
        store.execute("UPDATE students SET financial_hold = ? WHERE id = ?", hold, studentId);
    }

    public void addPrerequisite(String courseCode, String prerequisite) {
        store.execute("INSERT OR IGNORE INTO prerequisites (course_code, prerequisite) VALUES (?, ?)",
                courseCode, prerequisite);
    }

    public Set<String> prerequisitesOf(String courseCode) {
        return new HashSet<>(store.list("SELECT prerequisite FROM prerequisites WHERE course_code = ?",
                row -> row.getString(1), courseCode));
    }

    public EnrollmentRequest save(EnrollmentRequest request) {
        if (request.id() == null) {
            request.assignId(store.insert(
                    "INSERT INTO enrollment_requests (reference, student_id, course_code, status, note) VALUES (?, ?, ?, ?, ?)",
                    request.reference(), request.studentId(), request.courseCode(), request.status().name(), request.note()));
        } else {
            store.execute("UPDATE enrollment_requests SET status = ?, note = ? WHERE id = ?",
                    request.status().name(), request.note(), request.id());
        }
        return request;
    }

    public Optional<EnrollmentRequest> findRequest(long id) {
        return store.first("SELECT * FROM enrollment_requests WHERE id = ?", EligibilityRepository::request, id);
    }

    public Optional<EnrollmentRequest> latestByReference(String reference) {
        return store.first("SELECT * FROM enrollment_requests WHERE reference = ? ORDER BY id DESC LIMIT 1",
                EligibilityRepository::request, reference);
    }

    public boolean hasApproved(String studentId, String courseCode) {
        return store.first("SELECT id FROM enrollment_requests WHERE student_id = ? AND course_code = ? AND status = ?",
                row -> row.getLong(1), studentId, courseCode, EnrollmentRequest.Status.APPROVED.name()).isPresent();
    }

    public List<EnrollmentRequest> requests() {
        return store.list("SELECT * FROM enrollment_requests ORDER BY id", EligibilityRepository::request);
    }

    private static StudentRecord student(ResultSet row) throws SQLException {
        Set<String> completed = Arrays.stream(row.getString("completed").split(","))
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());
        return new StudentRecord(row.getString("id"), row.getString("name"), completed, row.getString("financial_hold"));
    }

    private static EnrollmentRequest request(ResultSet row) throws SQLException {
        return new EnrollmentRequest(row.getLong("id"), row.getString("reference"), row.getString("student_id"),
                row.getString("course_code"), EnrollmentRequest.Status.valueOf(row.getString("status")),
                row.getString("note"));
    }
}
