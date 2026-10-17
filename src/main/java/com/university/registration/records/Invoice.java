package com.university.registration.records;

import java.util.Locale;

public final class Invoice {

    public enum Status {
        ISSUED, PAID, VOIDED;

        public boolean canMoveTo(Status next) {
            return this == ISSUED && (next == PAID || next == VOIDED);
        }
    }

    private Long id;
    private final String reference;
    private final String studentId;
    private final String courseCode;
    private final long amount;
    private Status status;

    public Invoice(Long id, String reference, String studentId, String courseCode, long amount, Status status) {
        this.id = id;
        this.reference = reference;
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.amount = amount;
        this.status = status;
    }

    public static Invoice issue(String reference, String studentId, String courseCode, long amount) {
        return new Invoice(null, reference, studentId, courseCode, amount, Status.ISSUED);
    }

    public void pay() {
        moveTo(Status.PAID);
    }

    public void voidInvoice() {
        moveTo(Status.VOIDED);
    }

    private void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Invoice cannot move from " + status + " to " + next);
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

    public long amount() {
        return amount;
    }

    public Status status() {
        return status;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Invoice #%d %-7s %-4s %-8s %,d KZT %s", id, reference, studentId, courseCode, amount, status);
    }
}
