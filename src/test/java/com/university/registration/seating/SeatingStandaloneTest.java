package com.university.registration.seating;

import com.university.registration.seating.SeatReservation.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatingStandaloneTest {

    @TempDir
    Path dataDir;

    private SeatingService seating;

    @BeforeEach
    void freshStore() {
        new SeatingRepository(dataDir).saveSection(new CourseSection("CS301", "Algorithms", 5, "2026-FALL", 1, 1));
        seating = serviceOn(dataDir);
    }

    private static SeatingService serviceOn(Path dir) {
        SeatingRepository repository = new SeatingRepository(dir);
        return new SeatingService(repository, new SeatAllocator(), new WaitlistManager(repository));
    }

    @Test
    void reservedSeatIsHeldThenConfirmedAndSurvivesRestart() {
        SeatReservation seat = seating.reserve("REF-1", "S1", "CS301");
        seating.confirm("REF-1");

        SeatingService restarted = serviceOn(dataDir);

        assertEquals(Status.HELD, seat.status());
        assertEquals(Status.CONFIRMED, restarted.seatFor("REF-1").orElseThrow().status());
        assertEquals(0, restarted.freeSeats("CS301"));
    }

    @Test
    void fullSectionPutsStudentOnWaitlistThenClosesIt() {
        seating.reserve("REF-1", "S1", "CS301");

        SeatUnavailableException waitlisted = assertThrows(SeatUnavailableException.class,
                () -> seating.reserve("REF-2", "S2", "CS301"));
        SeatUnavailableException stillWaiting = assertThrows(SeatUnavailableException.class,
                () -> seating.reserve("REF-3", "S2", "CS301"));
        SeatUnavailableException closed = assertThrows(SeatUnavailableException.class,
                () -> seating.reserve("REF-4", "S3", "CS301"));

        assertTrue(waitlisted.getMessage().contains("waitlist at position 1"));
        assertTrue(stillWaiting.getMessage().contains("stays on the waitlist"));
        assertTrue(closed.getMessage().contains("waitlist is closed"));
        assertEquals("S2", seating.nextInLine("CS301").orElseThrow().studentId());
        assertTrue(seating.seatFor("REF-2").isEmpty());
    }

    @Test
    void releasingASeatLetsTheWaitlistedStudentInAndRemovesThemFromTheQueue() {
        seating.reserve("REF-1", "S1", "CS301");
        assertThrows(SeatUnavailableException.class, () -> seating.reserve("REF-2", "S2", "CS301"));

        assertEquals(Status.RELEASED, seating.release("REF-1").orElseThrow().status());
        assertTrue(seating.release("REF-1").isEmpty());
        SeatReservation seat = seating.reserve("REF-3", "S2", "CS301");

        assertEquals(Status.HELD, seat.status());
        assertTrue(seating.waitlist("CS301").isEmpty());
        assertEquals(3, seating.reservations().size());
    }

    @Test
    void capacityChangesArePersisted() {
        seating.changeCapacity("CS301", 0);

        assertEquals(0, serviceOn(dataDir).section("CS301").orElseThrow().capacity());
        assertThrows(SeatUnavailableException.class, () -> seating.changeCapacity("NOPE", 3));
        assertThrows(SeatUnavailableException.class, () -> seating.reserve("REF-1", "S1", "NOPE"));
        assertEquals(0, seating.freeSeats("NOPE"));
        assertEquals(1, seating.sections().size());
    }

    @Test
    void reservationEnforcesItsOwnLegalTransitions() {
        SeatReservation waiting = SeatReservation.waitlist("REF-1", "S1", "CS301");
        assertThrows(IllegalStateException.class, waiting::confirm);
        waiting.release();
        assertThrows(IllegalStateException.class, waiting::release);

        SeatReservation held = SeatReservation.hold("REF-2", "S1", "CS301");
        held.confirm();
        assertThrows(IllegalStateException.class, held::confirm);
        assertTrue(held.status().occupiesSeat());
        assertFalse(Status.WAITLISTED.occupiesSeat());
        assertTrue(held.toString().contains("CONFIRMED"));
    }

    @Test
    void allocatorDecidesFromCountsAlone() {
        SeatAllocator allocator = new SeatAllocator();
        CourseSection section = new CourseSection("X", "X", 3, "T", 2, 1);

        assertEquals(SeatAllocator.Decision.SEAT, allocator.decide(section, 1, 0));
        assertEquals(SeatAllocator.Decision.WAITLIST, allocator.decide(section, 2, 0));
        assertEquals(SeatAllocator.Decision.NONE, allocator.decide(section, 2, 1));
        assertEquals(0, allocator.freeSeats(section, 5));
        assertThrows(IllegalArgumentException.class, () -> new CourseSection("Y", "Y", 0, "T", 1, 1));
    }
}
