package com.university.registration.seating;

import com.university.registration.storage.SqliteStore;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SeatingRepository {

    public static final String FILE_NAME = "seating.db";

    private final SqliteStore store;

    public SeatingRepository(Path dataDir) {
        store = new SqliteStore(dataDir.resolve(FILE_NAME),
                "CREATE TABLE IF NOT EXISTS sections (code TEXT PRIMARY KEY, title TEXT NOT NULL, credits INTEGER NOT NULL, "
                        + "term TEXT NOT NULL, capacity INTEGER NOT NULL, waitlist_capacity INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS reservations (id INTEGER PRIMARY KEY AUTOINCREMENT, reference TEXT NOT NULL, "
                        + "student_id TEXT NOT NULL, course_code TEXT NOT NULL, status TEXT NOT NULL)");
    }

    public void saveSection(CourseSection section) {
        store.execute("INSERT OR REPLACE INTO sections (code, title, credits, term, capacity, waitlist_capacity) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                section.code(), section.title(), section.credits(), section.term(), section.capacity(),
                section.waitlistCapacity());
    }

    public Optional<CourseSection> findSection(String code) {
        return store.first("SELECT * FROM sections WHERE code = ?", SeatingRepository::section, code);
    }

    public List<CourseSection> sections() {
        return store.list("SELECT * FROM sections ORDER BY code", SeatingRepository::section);
    }

    public SeatReservation save(SeatReservation reservation) {
        if (reservation.id() == null) {
            reservation.assignId(store.insert(
                    "INSERT INTO reservations (reference, student_id, course_code, status) VALUES (?, ?, ?, ?)",
                    reservation.reference(), reservation.studentId(), reservation.courseCode(), reservation.status().name()));
        } else {
            store.execute("UPDATE reservations SET status = ? WHERE id = ?", reservation.status().name(), reservation.id());
        }
        return reservation;
    }

    public Optional<SeatReservation> seatFor(String reference) {
        return store.first("SELECT * FROM reservations WHERE reference = ? AND status IN ('HELD', 'CONFIRMED') "
                + "ORDER BY id DESC LIMIT 1", SeatingRepository::reservation, reference);
    }

    public int occupiedSeats(String courseCode) {
        return store.first("SELECT COUNT(*) FROM reservations WHERE course_code = ? AND status IN ('HELD', 'CONFIRMED')",
                row -> row.getInt(1), courseCode).orElse(0);
    }

    public List<SeatReservation> waitlist(String courseCode) {
        return store.list("SELECT * FROM reservations WHERE course_code = ? AND status = 'WAITLISTED' ORDER BY id",
                SeatingRepository::reservation, courseCode);
    }

    public List<SeatReservation> reservations() {
        return store.list("SELECT * FROM reservations ORDER BY id", SeatingRepository::reservation);
    }

    private static CourseSection section(ResultSet row) throws SQLException {
        return new CourseSection(row.getString("code"), row.getString("title"), row.getInt("credits"),
                row.getString("term"), row.getInt("capacity"), row.getInt("waitlist_capacity"));
    }

    private static SeatReservation reservation(ResultSet row) throws SQLException {
        return new SeatReservation(row.getLong("id"), row.getString("reference"), row.getString("student_id"),
                row.getString("course_code"), SeatReservation.Status.valueOf(row.getString("status")));
    }
}
