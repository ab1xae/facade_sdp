package com.university.registration.records;

public record TranscriptEntry(long id, String reference, String studentId, String courseCode, String term, String status) {

    public static final String REGISTERED = "REGISTERED";
    public static final String REMOVED = "REMOVED";

    @Override
    public String toString() {
        return String.format("Transcript #%d %-7s %-4s %-8s %s %s", id, reference, studentId, courseCode, term, status);
    }
}
