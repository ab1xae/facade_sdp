package com.university.registration.seating;

import java.util.List;
import java.util.Optional;

public final class WaitlistManager {

    private final SeatingRepository repository;

    public WaitlistManager(SeatingRepository repository) {
        this.repository = repository;
    }

    public int join(String reference, String studentId, String courseCode) {
        if (positionOf(studentId, courseCode) == 0) {
            repository.save(SeatReservation.waitlist(reference, studentId, courseCode));
        }
        return positionOf(studentId, courseCode);
    }

    public int positionOf(String studentId, String courseCode) {
        List<SeatReservation> queue = repository.waitlist(courseCode);
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).studentId().equals(studentId)) {
                return i + 1;
            }
        }
        return 0;
    }

    public void leave(String studentId, String courseCode) {
        entryOf(studentId, courseCode).ifPresent(entry -> {
            entry.release();
            repository.save(entry);
        });
    }

    public Optional<SeatReservation> nextInLine(String courseCode) {
        return repository.waitlist(courseCode).stream().findFirst();
    }

    public List<SeatReservation> queue(String courseCode) {
        return repository.waitlist(courseCode);
    }

    private Optional<SeatReservation> entryOf(String studentId, String courseCode) {
        return repository.waitlist(courseCode).stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .findFirst();
    }
}
