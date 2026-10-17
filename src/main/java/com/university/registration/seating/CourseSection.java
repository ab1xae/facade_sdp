package com.university.registration.seating;

public record CourseSection(String code, String title, int credits, String term, int capacity, int waitlistCapacity) {

    public CourseSection {
        if (capacity < 0 || waitlistCapacity < 0 || credits <= 0) {
            throw new IllegalArgumentException("Invalid section " + code);
        }
    }

    public CourseSection withCapacity(int newCapacity) {
        return new CourseSection(code, title, credits, term, newCapacity, waitlistCapacity);
    }
}
