package com.university.registration.eligibility;

public final class EnrollmentRequest {

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case PENDING -> next == APPROVED || next == REJECTED;
                case APPROVED -> next == CANCELLED;
                case REJECTED, CANCELLED -> false;
            };
        }
    }

    private Long id;
    private final String reference;
    private final String studentId;
    private final String courseCode;
    private Status status;
    private String note;

    public EnrollmentRequest(Long id, String reference, String studentId, String courseCode, Status status, String note) {
        this.id = id;
        this.reference = reference;
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.status = status;
        this.note = note;
    }

    public static EnrollmentRequest open(String reference, String studentId, String courseCode) {
        return new EnrollmentRequest(null, reference, studentId, courseCode, Status.PENDING, null);
    }

    public void approve() {
        moveTo(Status.APPROVED, null);
    }

    public void reject(String reason) {
        moveTo(Status.REJECTED, reason);
    }

    public void cancel(String reason) {
        moveTo(Status.CANCELLED, reason);
    }

    private void moveTo(Status next, String newNote) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Enrollment request cannot move from " + status + " to " + next);
        }
        status = next;
        note = newNote;
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

    public String note() {
        return note;
    }

    @Override
    public String toString() {
        return String.format("Request #%d %-7s %-4s %-8s %s%s", id, reference, studentId, courseCode, status,
                note == null ? "" : "  (" + note + ")");
    }
}
