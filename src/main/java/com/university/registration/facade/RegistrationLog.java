package com.university.registration.facade;

import com.university.registration.storage.SqliteStore;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class RegistrationLog {

    public static final String FILE_NAME = "registrations.db";

    private final SqliteStore store;

    public RegistrationLog(Path dataDir) {
        store = new SqliteStore(dataDir.resolve(FILE_NAME),
                "CREATE TABLE IF NOT EXISTS registrations (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id TEXT NOT NULL, "
                        + "course_code TEXT NOT NULL, status TEXT NOT NULL, failed_stage TEXT, reason TEXT)");
    }

    public Registration create(String studentId, String courseCode) {
        long id = store.insert("INSERT INTO registrations (student_id, course_code, status) VALUES (?, ?, ?)",
                studentId, courseCode, Registration.Status.STARTED.name());
        return new Registration(id, studentId, courseCode, Registration.Status.STARTED, null, null);
    }

    public Registration save(Registration registration) {
        store.execute("UPDATE registrations SET status = ?, failed_stage = ?, reason = ? WHERE id = ?",
                registration.status().name(), registration.failedStage(), registration.reason(), registration.id());
        return registration;
    }

    public Optional<Registration> find(long id) {
        return store.first("SELECT * FROM registrations WHERE id = ?", RegistrationLog::registration, id);
    }

    public List<Registration> all() {
        return store.list("SELECT * FROM registrations ORDER BY id", RegistrationLog::registration);
    }

    public List<Registration> inFlight() {
        return store.list("SELECT * FROM registrations WHERE status IN ('STARTED', 'PENDING_B', 'PENDING_C', 'COMPENSATING') "
                + "ORDER BY id", RegistrationLog::registration);
    }

    private static Registration registration(ResultSet row) throws SQLException {
        return new Registration(row.getLong("id"), row.getString("student_id"), row.getString("course_code"),
                Registration.Status.valueOf(row.getString("status")), row.getString("failed_stage"),
                row.getString("reason"));
    }
}
