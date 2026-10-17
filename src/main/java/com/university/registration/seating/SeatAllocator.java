package com.university.registration.seating;

public final class SeatAllocator {

    public enum Decision { SEAT, WAITLIST, NONE }

    public Decision decide(CourseSection section, int occupiedSeats, int waitingStudents) {
        if (occupiedSeats < section.capacity()) {
            return Decision.SEAT;
        }
        if (waitingStudents < section.waitlistCapacity()) {
            return Decision.WAITLIST;
        }
        return Decision.NONE;
    }

    public int freeSeats(CourseSection section, int occupiedSeats) {
        return Math.max(0, section.capacity() - occupiedSeats);
    }
}
