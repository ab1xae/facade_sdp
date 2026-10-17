package com.university.registration.seating;

import java.util.List;
import java.util.Optional;

public final class SeatingService {

    private final SeatingRepository repository;
    private final SeatAllocator allocator;
    private final WaitlistManager waitlist;

    public SeatingService(SeatingRepository repository, SeatAllocator allocator, WaitlistManager waitlist) {
        this.repository = repository;
        this.allocator = allocator;
        this.waitlist = waitlist;
    }

    public SeatReservation reserve(String reference, String studentId, String courseCode) {
        CourseSection section = repository.findSection(courseCode)
                .orElseThrow(() -> new SeatUnavailableException("Unknown section " + courseCode));
        int occupied = repository.occupiedSeats(courseCode);
        SeatAllocator.Decision decision = allocator.decide(section, occupied, waitlist.queue(courseCode).size());
        int position = waitlist.positionOf(studentId, courseCode);
        if (decision != SeatAllocator.Decision.SEAT && position > 0) {
            throw new SeatUnavailableException(courseCode + " is still full; " + studentId
                    + " stays on the waitlist at position " + position);
        }
        return switch (decision) {
            case SEAT -> {
                waitlist.leave(studentId, courseCode);
                yield repository.save(SeatReservation.hold(reference, studentId, courseCode));
            }
            case WAITLIST -> throw new SeatUnavailableException(courseCode + " is full (" + section.capacity()
                    + " seats); " + studentId + " placed on the waitlist at position "
                    + waitlist.join(reference, studentId, courseCode));
            case NONE -> throw new SeatUnavailableException(courseCode + " is full and its waitlist is closed");
        };
    }

    public void confirm(String reference) {
        repository.seatFor(reference)
                .filter(seat -> seat.status() == SeatReservation.Status.HELD)
                .ifPresent(seat -> {
                    seat.confirm();
                    repository.save(seat);
                });
    }

    public Optional<SeatReservation> release(String reference) {
        return repository.seatFor(reference).map(seat -> {
            seat.release();
            return repository.save(seat);
        });
    }

    public Optional<SeatReservation> seatFor(String reference) {
        return repository.seatFor(reference);
    }

    public void changeCapacity(String courseCode, int capacity) {
        CourseSection section = repository.findSection(courseCode)
                .orElseThrow(() -> new SeatUnavailableException("Unknown section " + courseCode));
        repository.saveSection(section.withCapacity(capacity));
    }

    public Optional<CourseSection> section(String courseCode) {
        return repository.findSection(courseCode);
    }

    public List<CourseSection> sections() {
        return repository.sections();
    }

    public int occupiedSeats(String courseCode) {
        return repository.occupiedSeats(courseCode);
    }

    public int freeSeats(String courseCode) {
        return section(courseCode).map(section -> allocator.freeSeats(section, occupiedSeats(courseCode))).orElse(0);
    }

    public List<SeatReservation> waitlist(String courseCode) {
        return waitlist.queue(courseCode);
    }

    public Optional<SeatReservation> nextInLine(String courseCode) {
        return waitlist.nextInLine(courseCode);
    }

    public List<SeatReservation> reservations() {
        return repository.reservations();
    }
}
