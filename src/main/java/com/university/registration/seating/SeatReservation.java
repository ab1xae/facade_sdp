package com.university.registration.seating;

public final class SeatReservation {

    public enum Status {
        HELD, CONFIRMED, WAITLISTED, RELEASED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case HELD -> next == CONFIRMED || next == RELEASED;
                case CONFIRMED, WAITLISTED -> next == RELEASED;
                case RELEASED -> false;
            };
        }

        public boolean occupiesSeat() {
            return this == HELD || this == CONFIRMED;
        }
    }

    private Long id;
    private final String reference;
    private final String studentId;
    private final String courseCode;
    private Status status;

    public SeatReservation(Long id, String reference, String studentId, String courseCode, Status status) {
        this.id = id;
        this.reference = reference;
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.status = status;
    }

    public static SeatReservation hold(String reference, String studentId, String courseCode) {
        return new SeatReservation(null, reference, studentId, courseCode, Status.HELD);
    }

    public static SeatReservation waitlist(String reference, String studentId, String courseCode) {
        return new SeatReservation(null, reference, studentId, courseCode, Status.WAITLISTED);
    }

    public void confirm() {
        moveTo(Status.CONFIRMED);
    }

    public void release() {
        moveTo(Status.RELEASED);
    }

    private void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Seat reservation cannot move from " + status + " to " + next);
        }
        status = next;
    }

    void assignId(long newId) {
        id = newId;
    }

    public Long id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public String studentId() {
        return studentId;
    }

    public String courseCode() {
        return courseCode;
    }

    public Status status() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("Seat #%d %-7s %-4s %-8s %s", id, reference, studentId, courseCode, status);
    }
}
