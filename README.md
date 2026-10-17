# University Registration — Facade

The client calls one method: **register a student for a course**. Behind it, three independent subsystems run in order:

```
A eligibility  ->  B seating  ->  C records
(can join?)        (free seat?)   (invoice + transcript)
```

If B or C fails, the facade undoes the steps that already succeeded.

![UML](docs/uml/class-diagram.png)

## Run

Needs JDK 17+.

```
mvnw.cmd -q compile exec:java                          run the app
mvnw.cmd -q compile exec:java -Dexec.args="--reset"    run with a clean database
mvnw.cmd verify                                        tests + coverage
```

Data is stored in `data/` (four SQLite files). To reset, run with `--reset` or delete the folder.

## Structure

| Part | Classes | Status |
|---|---|---|
| A `eligibility` | EligibilityService, PrerequisiteChecker, FinancialHoldChecker, EligibilityRepository | EnrollmentRequest: PENDING, APPROVED, REJECTED, CANCELLED |
| B `seating` | SeatingService, SeatAllocator, WaitlistManager, SeatingRepository | SeatReservation: HELD, CONFIRMED, WAITLISTED, RELEASED |
| C `records` | BillingSystem, TranscriptService, TuitionCalculator, RecordsRepository | Invoice: ISSUED, PAID, VOIDED |
| Facades | RegistrationFacade (register, resume), RegistrarAdminFacade (inspect, undo, failure switches) | Registration: STARTED, PENDING_B, PENDING_C, COMPLETED, REJECTED, COMPENSATING, COMPENSATED |

The subsystems do not know about each other; they only meet inside the facade.

## Failures and undo

| What fails | Example | What is undone |
|---|---|---|
| A | financial hold | nothing |
| B after A | section is full | A |
| C after A and B | credit limit exceeded | B, then A |
| app crash | process killed mid-chain | on restart: continue or undo |

The facade decides what to undo by reading what each subsystem actually saved on disk, not from memory.

## Demo menu

- **2:** ready scenarios (success, rejection, B fails, C fails)
- **4:** show the saved data
- **8:** turn failures on (billing offline, term locked, capacity, hold)
- **9:** crash the app mid-chain
- **6:** recover after restart

## Least Knowledge

Without a facade, the client talks to every subsystem and undoes them by hand:

```java
eligibility.submit(ref, "S3", "MATH201");
seating.reserve(ref, "S3", "MATH201");
int credits = seating.section("MATH201").orElseThrow().credits();
try {
    billing.issue(ref, "S3", "MATH201", credits);
} catch (FinalizationException e) {
    seating.release(ref);
    eligibility.cancel(ref, e.getMessage());
}
```

With a facade, the client talks to one object:

```java
registration.register("S3", "MATH201");
```

## Facade vs Adapter

An Adapter changes **one** interface so it fits the client: it solves *incompatibility*. Our subsystems are not
incompatible; each works fine on its own. The problem is *complexity*: three subsystems must be called in order, the
progress must be saved, and failed steps must be undone. A Facade solves exactly that: it gives one simple operation,
`register()`, over several subsystems.

## Tests

47 tests, 98 % line coverage (minimum 80 %, checked by JaCoCo). The `app` package (console I/O) is excluded; it is
shown in the live demo.

- each subsystem tested alone;
- B fails after A, and C fails after A and B;
- restart in the middle of a chain;
- every test uses its own empty temporary folder (F.I.R.S.T.).

Tests are written in a small domain language (`RegistrationWorld`):

```java
world.givenStudent("S3").owing(380_000).withCreditLimit(400_000);
Outcome outcome = world.whenStudent("S3").registersFor("CS201");
outcome.shouldEndAs(COMPENSATED).undoingInOrder("B", "A");
```
