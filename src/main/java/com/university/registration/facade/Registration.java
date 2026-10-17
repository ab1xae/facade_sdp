package com.university.registration.facade;

public final class Registration {

    public enum Status {
        STARTED, PENDING_B, PENDING_C, COMPLETED, REJECTED, COMPENSATING, COMPENSATED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case STARTED -> next == PENDING_B || next == REJECTED || next == COMPENSATING;
                case PENDING_B -> next == PENDING_C || next == COMPENSATING;
                case PENDING_C -> next == COMPLETED || next == COMPENSATING;
                case COMPLETED -> next == COMPENSATING;
                case COMPENSATING -> next == COMPENSATED;
                case REJECTED, COMPENSATED -> false;
            };
        }

        public boolean inFlight() {
            return this == STARTED || this == PENDING_B || this == PENDING_C || this == COMPENSATING;
        }
    }

    private final long id;
    private final String studentId;
    private final String courseCode;
    private Status status;
    private String failedStage;
    private String reason;

    public Registration(long id, String studentId, String courseCode, Status status, String failedStage, String reason) {
        this.id = id;
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.status = status;
        this.failedStage = failedStage;
        this.reason = reason;
    }

    void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException(reference() + " cannot move from " + status + " to " + next);
        }
        status = next;
    }

    void reject(String rejection) {
        moveTo(Status.REJECTED);
        failedStage = "A";
        reason = rejection;
    }

    void startCompensation(String stage, String cause) {
        moveTo(Status.COMPENSATING);
        failedStage = stage;
        reason = cause;
    }

    public String reference() {
        return "REG-" + id;
    }

    public long id() {
        return id;
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

    public String failedStage() {
        return failedStage;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        String failure = failedStage == null ? "" : "  [stage " + failedStage + ": " + reason + "]";
        return String.format("%-7s %-4s -> %-8s %s%s", reference(), studentId, courseCode, status, failure);
    }
}
