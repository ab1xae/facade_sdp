# University Registration — Facade over a Three-Stage Chain

Assignment 4, variant **7 — University Registration**.

A client asks for one thing: *"register student S1 for course SE310"*. Behind that single call three independent
subsystems must all succeed, each persisting its own state in its own SQLite file:

| Stage | Subsystem (package) | What it does | Own entity and statuses |
|---|---|---|---|
| **A** intake / reservation | `eligibility` — Prerequisite Checker, Financial Hold Checker | checks holds, prerequisites, duplicates; approves an enrollment request | `EnrollmentRequest`: `PENDING → APPROVED / REJECTED`, `APPROVED → CANCELLED` |
| **B** processing | `seating` — Seat Allocator, Waitlist Manager | holds a seat in the section or puts the student on the waitlist | `SeatReservation`: `HELD → CONFIRMED`, `HELD/CONFIRMED/WAITLISTED → RELEASED` |
| **C** finalization | `records` — Billing System, Transcript Service | issues the tuition invoice, records the course on the transcript | `Invoice`: `ISSUED → PAID / VOIDED` |

The chain itself is persisted in `registrations.db`:
`STARTED → PENDING_B → PENDING_C → COMPLETED`, or `REJECTED` (stage A said no), or `COMPENSATING → COMPENSATED`.

![UML class diagram](docs/uml/class-diagram.png)

The diagram is also available as [SVG](docs/uml/class-diagram.svg) and as [PlantUML source](docs/uml/class-diagram.puml).

---

## 1. Build, run, reset

Requires **JDK 17+** (`JAVA_HOME` pointing to it). Maven is not needed: the Maven Wrapper downloads it.

| What | Windows | Linux / macOS |
|---|---|---|
| Run the demo app | `mvnw.cmd -q compile exec:java` | `./mvnw -q compile exec:java` |
| Run on a **clean store** | `mvnw.cmd -q compile exec:java -Dexec.args="--reset"` | `./mvnw -q compile exec:java -Dexec.args="--reset"` |
| Tests + coverage report | `mvnw.cmd test` | `./mvnw test` |
| Tests + 80 % coverage gate | `mvnw.cmd verify` | `./mvnw verify` |
| Subsystems used standalone | `mvnw.cmd -q compile exec:java -Dexec.mainClass=com.university.registration.app.StandaloneDemo` | same with `./mvnw` |

**Resetting the persisted store.** Everything lives in `./data/` (`eligibility.db`, `seating.db`, `records.db`,
`registrations.db`). Either start with `--reset` (wipes the four files and re-seeds demo data) or simply delete the
`data/` folder; it is re-seeded on the next start. `--data <dir>` points the app at another folder.

Coverage report: `target/site/jacoco/index.html`.

---

## 2. Live demo (what to run at the defense)

Seeded data (`DemoData`):

| Student | Situation | Used for |
|---|---|---|
| S1 Aliya | completed CS101, CS201, MATH101; limit 600 000 KZT | happy path, B-failure |
| S2 Bekzat | financial hold "Unpaid library fine" | rejection at A |
| S3 Dana | owes 380 000 of a 400 000 KZT limit | C-failure (billing) |
| S4 Timur | completed nothing | prerequisite rejection, crash demo |
| S5 Madina | 50 % scholarship; already holds the only seat in CS301 | makes CS301 full |

Tuition is 25 000 KZT per credit. Courses: CS101, CS201, CS301 (1 seat, waitlist 2), SE310, MATH201, HIST100.

Main menu:

```
 1) Register a student for a course (A -> B -> C)
 2) Prepared scenarios (happy path, rejections, both compensation depths)
 3) Students and course catalog
 4) Show persisted state of all four stores
 5) Inspect one registration
 6) Recover interrupted registrations (resume or compensate)
 7) Admin: compensate / withdraw a registration manually
 8) Failure switches (billing outage, term lock, capacity, holds, payment)
 9) Register and CRASH the process after a committed step
```

Option **2** gives one-key access to every required scenario:

| # | Scenario | Result |
|---|---|---|
| 1 | S1 → SE310 | `COMPLETED`: request APPROVED, seat CONFIRMED, invoice 150 000 ISSUED, transcript REGISTERED |
| 2 | S2 → HIST100 | `REJECTED` at A (financial hold), nothing to undo |
| 3 | S4 → CS201 | `REJECTED` at A (missing CS101) |
| 4 | **B fails after A**: S1 → CS301 | section full, S1 goes on the waitlist → **undo A**: request CANCELLED |
| 5 | **C fails after A and B**: S3 → MATH201 | billing rejects (380 000 + 100 000 > 400 000) → **undo B, then A** |
| 6 | C fails after its own invoice | transcript term locked → void invoice (C), release seat (B), cancel request (A) |
| 7 | S99 → CS101 | `INVALID`, nothing persisted |

Sample output of scenario 5:

```
FAILED    REG-5 failed at stage C: Billing rejected: balance 380,000 + tuition 100,000 exceeds credit limit 400,000 KZT
    compensation, in the order it was executed:
      B: released seat #4 in MATH201
      A: cancelled enrollment request #5
    persisted now:
      Chain        REG-5   S3   -> MATH201  COMPENSATED  [stage C: Billing rejected: ...]
      A eligibility Request #5 REG-5   S3   MATH201  CANCELLED  (Compensated: Billing rejected: ...)
      B seating     none
      C invoice     none
      C transcript  none
```

**Unannounced failures.** Option **8** changes the real persisted state, and the switches survive a restart:
- billing outage, which makes C fail;
- transcript term lock, which makes C fail after the invoice is issued;
- section capacity, for example 0, which makes B fail;
- financial hold, which makes A reject;
- paying an invoice, which blocks a later withdrawal.

Any registration can be withdrawn from option **7**; a completed one is unwound C → B → A.

**Restart demo.** Option **9** arms a crash after A (`PENDING_B` on disk) or after B (`PENDING_C`) and kills the JVM
with `Runtime.halt`. On the next start the app reads `registrations.db` and reports:

```
!!  Found 1 interrupted registration(s) on disk:
      REG-7   S4   -> CS101    PENDING_C
!!  Use option 6 to resume or compensate them.
```

Option **6** then resumes the chain forward (only the missing steps run) or compensates it back.

---

## 3. Design

```
com.university.registration
├── eligibility   A: EligibilityService, PrerequisiteChecker, FinancialHoldChecker, EligibilityRepository, EnrollmentRequest
├── seating       B: SeatingService, SeatAllocator, WaitlistManager, SeatingRepository, SeatReservation
├── records       C: BillingSystem, TranscriptService, TuitionCalculator, RecordsRepository, Invoice
├── facade        RegistrationFacade, RegistrarAdminFacade, ChainCompensator, Registration, RegistrationLog, exceptions
├── storage       SqliteStore: generic JDBC helper, no domain knowledge
└── app           RegistrarCli (client), Registrar (composition root), DemoData, StandaloneDemo
```

**Subsystems (3.1).** Each has five classes (15 in total), plus one value record and one exception:
- **Business-logic classes**, which hold no state: `PrerequisiteChecker`, `FinancialHoldChecker`, `SeatAllocator`, `TuitionCalculator`.
- **Persistence gateways**, which talk to their own SQLite file directly: `EligibilityRepository`, `SeatingRepository`, `RecordsRepository`.
- **Status rules.** Each entity enforces its own legal transitions through `Status.canMoveTo`, independent of the facade.

No file in `eligibility`, `seating` or `records` imports another of those packages, or `facade`; they share only
the generic `storage.SqliteStore`. Subsystems meet only inside `facade`. `StandaloneDemo` and the `*StandaloneTest` classes use each
subsystem with no facade at all.

**Facades (3.2).**
- `RegistrationFacade` is the client workflow:
  - `register` validates input, creates the chain record and runs `drive()`;
  - `resume` continues an interrupted chain from its persisted status.
  - `drive()` holds the sequencing and decision logic: it advances through `STARTED → PENDING_B → PENDING_C`, persists every step, and on a subsystem exception decides which compensation is needed.
- `RegistrarAdminFacade` is the narrower administrative workflow:
  - inspection: `inspect`, `stateReport`, `interrupted`;
  - manual compensation and withdrawal, refused when the invoice is already paid;
  - finishing compensations that a crash interrupted;
  - the failure switches used in the demo.

**Durable state and the chain record (3.3).** Every step is written to disk before the next one starts. Each
subsystem stores the registration's `reference` (`REG-n`) next to its own row. This is an opaque correlation id, so
it does not make one subsystem know about another.

**Compensation (3.4).** When stage B or C fails, `ChainCompensator.unwind` runs the following steps:
1. It persists `COMPENSATING` with the failed stage and the reason first. A crash during compensation is therefore visible after a restart.
2. It asks each subsystem what it **actually has on disk** for that reference, in reverse order:
   - transcript entry (C), then invoice (C);
   - seat (B);
   - enrollment request (A).
3. It undoes only what exists.
4. It persists `COMPENSATED` and returns the list of undo actions, in the order they were executed.

As a result:
- **B fails after A**: there is no seat or invoice, so only A is undone (`undoingInOrder("A")`).
- **C fails after A and B**: the seat is released first, then the request is cancelled (`undoingInOrder("B", "A")`).
- **C fails after its own invoice**: C → B → A.

Every undo is idempotent; for example, `voidFor` only touches an `ISSUED` invoice. Re-running a compensation after a
crash is therefore safe.

`resume` relies on the same persisted lookups. Suppose the process died after a subsystem committed but before the
chain log was updated. `drive()` sees, for example, an approved request for that reference, does not submit it a
second time, and simply moves on (see `resumeTrustsWhatTheSubsystemPersistedEvenIfTheChainLogLagsBehind`).

**Further failure handling (3.6).** Custom exceptions:
- **Subsystem exceptions**: `EligibilityException`, `SeatUnavailableException`, `FinalizationException`.
- **Facade-level exceptions**: `InvalidRegistrationException` (bad or unknown input; nothing is persisted), `RegistrationRejectedException` (stage A said no; there is nothing to undo) and `RegistrationFailedException` (carries the compensation report).
- **Other refusals**:
  - billing is offline;
  - the transcript term is locked;
  - the invoice is already paid, so the student cannot be withdrawn;
  - illegal status transitions (`IllegalStateException` thrown by every entity).

---

## 4. Principle of Least Knowledge — before / after

**Before: the client talks to every subsystem itself.** It holds six "friends" and reaches into their results
(`section.credits()`, `section.term()`, `seat.status()`). It also has to know the compensation order and the
idempotency rules of all three subsystems.

```java
// RegistrarCli without a facade
EligibilityService eligibility = new EligibilityService(new EligibilityRepository(dir),
        new PrerequisiteChecker(), new FinancialHoldChecker());
SeatingRepository seatingRepo = new SeatingRepository(dir);
SeatingService seating = new SeatingService(seatingRepo, new SeatAllocator(), new WaitlistManager(seatingRepo));
RecordsRepository records = new RecordsRepository(dir);
BillingSystem billing = new BillingSystem(records, new TuitionCalculator());
TranscriptService transcript = new TranscriptService(records);

String ref = "REG-" + nextId();
eligibility.submit(ref, "S3", "MATH201");
try {
    seating.reserve(ref, "S3", "MATH201");
} catch (SeatUnavailableException e) {
    eligibility.cancel(ref, e.getMessage());
    throw e;
}
CourseSection section = seating.section("MATH201").orElseThrow();      // stranger's data
try {
    billing.issue(ref, "S3", "MATH201", section.credits());             // reaching through
    transcript.record(ref, "S3", "MATH201", section.term());
} catch (FinalizationException e) {
    billing.voidFor(ref);                                               // client must know the
    seating.release(ref);                                               // right order C -> B -> A
    eligibility.cancel(ref, e.getMessage());
    throw e;
}
seating.confirm(ref);
// ...and nothing is persisted about the chain itself, so a crash here is unrecoverable
```

**After: the client has two immediate friends, the facades.** It calls their methods and only reads the results they return.

```java
// RegistrarCli
Registrar registrar = Registrar.open(dataDir, crash);
RegistrationFacade registration = registrar.registration();
RegistrarAdminFacade admin = registrar.admin();

try {
    Registration done = registration.register("S3", "MATH201");
} catch (RegistrationFailedException e) {
    e.compensations().forEach(System.out::println);   // "B: released seat #4", "A: cancelled request #5"
}
admin.inspect(5).lines().forEach(System.out::println);
```

The CLI imports nothing from `eligibility`, `seating` or `records`. Only the composition root (`Registrar`) and the
seeding code (`DemoData`) know the concrete subsystems.

---

## 5. Reflection: why a Facade and not an Adapter

An Adapter exists to make **one** existing class fit **one** interface the client already expects. The intent is
*conversion*: the client's needs are fixed and the adaptee speaks a different dialect. Nothing like that is going on
here. `EligibilityService`, `SeatingService`, `BillingSystem` and `TranscriptService` do not have the "wrong" interface.
Each has exactly the interface its own domain needs, and each is used as-is by its standalone tests and by
`StandaloneDemo`. Wrapping `SeatingService` in an adapter would not help the client at all. The client would still
have to call the other three subsystems in the right order, carry the reference between them, read the section's
credits and term, and remember to release the seat and cancel the request when billing says no.

The problem here is **orchestration and simplification**. The client wants a *new*, higher-level operation,
"register a student", that none of the subsystems offers on its own. That operation has to:
- sequence A → B → C;
- persist the progress of the chain;
- decide which partial subset to unwind when B or C fails;
- resume after a crash.

That is a Facade's intent: give one simple entry point over a set of collaborating subsystems, and keep their
coordination and failure semantics in one place. The second facade, `RegistrarAdminFacade`, confirms this. An adapter
always maps one interface to another, while a facade can expose different *views* of the same subsystems to different
clients: the student-facing workflow and the registrar's inspection and recovery workflow.

---

## 6. Tests

`./mvnw verify` runs 47 tests and enforces ≥ 80 % line coverage per package. Current results:

| Package | Line coverage |
|---|---|
| eligibility (A) | 99 % |
| seating (B) | 100 % |
| records (C) | 100 % |
| facade | 98 % |
| storage | 89 % |
| **total** | **98 %** |

The `app` package is excluded from JaCoCo. It holds the interactive console I/O and the composition root, which are
covered by the live demo rather than by unit tests. The composition root is still exercised by every facade test
through `RegistrationWorld`.

| Test class | What it covers |
|---|---|
| `EligibilityStandaloneTest`, `SeatingStandaloneTest`, `RecordsStandaloneTest` | each subsystem on its own: rules, transitions, persistence across new repository instances |
| `RegistrationFacadeTest` | happy path, invalid input, rejection at A, **B fails after A**, **C fails after A and B**, billing outage, C fails after its invoice, retry after compensation |
| `RegistrationRestartTest` | **store closed and reopened mid-chain**: crash at `PENDING_C` → restart → resume to `COMPLETED`; crash at `PENDING_B` → restart → compensate A only; chain log lagging behind a subsystem; compensation interrupted midway |
| `RegistrarAdminFacadeTest` | inspection, manual withdrawal C → C → B → A, paid-invoice guard, persisted failure switches |
| `SqliteStoreTest` | generated keys, rollback, error mapping |

**Domain-specific testing language.** `RegistrationWorld` (in `src/test`) makes each test read as a sentence in the
domain:

```java
world.givenStudent("S3").whoCompleted("CS101").owing(380_000).withCreditLimit(400_000);

Outcome outcome = world.whenStudent("S3").registersFor("CS201");

outcome.shouldEndAs(COMPENSATED).afterFailingAt("C").withReasonContaining("exceeds credit limit")
       .undoingInOrder("B", "A");
world.thenOnDisk(outcome).enrollmentRequestIs(CANCELLED).holdsNoSeat().hasNoOpenInvoice();
```

`thenOnDisk(...)` opens **new** repository instances on the data folder, so the assertions check what is persisted,
not what happens to be in memory. `world.crashingAfterReaching(PENDING_C)` and `world.restart()` simulate a process
crash and a restart.

**F.I.R.S.T.**
- **Fast:** each test uses small SQLite files; the whole suite runs in about 7 s.
- **Independent and Repeatable:** every test gets its own JUnit `@TempDir`, so it starts from an empty store. No shared state and no ordering assumptions.
- **Self-validating:** every test asserts its result.
- **Timely:** the git history adds each subsystem together with its tests, then the facades together with theirs.
