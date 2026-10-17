package com.university.registration.app;

import com.university.registration.facade.InvalidRegistrationException;
import com.university.registration.facade.RegistrarAdminFacade;
import com.university.registration.facade.Registration;
import com.university.registration.facade.RegistrationException;
import com.university.registration.facade.RegistrationFacade;
import com.university.registration.facade.RegistrationFailedException;
import com.university.registration.facade.RegistrationRejectedException;
import com.university.registration.facade.StepListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public final class RegistrarCli {

    private static final class EndOfInput extends RuntimeException {
    }

    private static final class CrashSwitch implements StepListener {
        private Registration.Status armedAt;

        @Override
        public void committed(Registration registration) {
            if (registration.status() == armedAt) {
                System.out.println();
                System.out.println("XX  SIMULATED CRASH: process killed right after " + registration.reference()
                        + " was persisted as " + registration.status());
                System.out.println("XX  Start the application again; it will find the interrupted chain on disk.");
                System.out.flush();
                Runtime.getRuntime().halt(3);
            }
        }
    }

    private final Scanner in;
    private final PrintStream out;
    private final Path dataDir;
    private final CrashSwitch crash = new CrashSwitch();
    private final RegistrationFacade registration;
    private final RegistrarAdminFacade admin;

    private RegistrarCli(Path dataDir, InputStream input, PrintStream output) {
        this.in = new Scanner(input);
        this.out = output;
        this.dataDir = dataDir;
        Registrar registrar = Registrar.open(dataDir, crash);
        this.registration = registrar.registration();
        this.admin = registrar.admin();
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of("data");
        boolean reset = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--reset")) {
                reset = true;
            } else if (args[i].equals("--data") && i + 1 < args.length) {
                dataDir = Path.of(args[++i]);
            } else {
                System.err.println("Usage: RegistrarCli [--reset] [--data <dir>]");
                return;
            }
        }
        if (reset) {
            DemoData.reset(dataDir);
            System.out.println("Persisted store wiped: " + dataDir.toAbsolutePath());
        }
        if (DemoData.seedIfEmpty(dataDir)) {
            System.out.println("Demo data seeded into " + dataDir.toAbsolutePath());
        }
        new RegistrarCli(dataDir, System.in, System.out).run();
    }

    private void run() {
        out.println("==============================================================");
        out.println(" University Registration  |  A eligibility -> B seating -> C records");
        out.println(" store: " + dataDir.toAbsolutePath());
        out.println("==============================================================");
        warnAboutInterrupted();
        try {
            while (true) {
                printMenu();
                String choice = ask("Choose");
                if (choice.equals("0")) {
                    return;
                }
                try {
                    switch (choice) {
                        case "1" -> register();
                        case "2" -> scenarios();
                        case "3" -> showCatalog();
                        case "4" -> print(admin.stateReport());
                        case "5" -> inspect();
                        case "6" -> recover();
                        case "7" -> compensateManually();
                        case "8" -> failureSwitches();
                        case "9" -> registerAndCrash();
                        default -> out.println("Unknown option");
                    }
                } catch (RegistrationException | IllegalArgumentException | IllegalStateException e) {
                    out.println("!   " + e.getMessage());
                }
            }
        } catch (EndOfInput e) {
            out.println();
        }
    }

    private void printMenu() {
        out.println();
        out.println(" 1) Register a student for a course (A -> B -> C)");
        out.println(" 2) Prepared scenarios (happy path, rejections, both compensation depths)");
        out.println(" 3) Students and course catalog");
        out.println(" 4) Show persisted state of all four stores");
        out.println(" 5) Inspect one registration");
        out.println(" 6) Recover interrupted registrations (resume or compensate)");
        out.println(" 7) Admin: compensate / withdraw a registration manually");
        out.println(" 8) Failure switches (billing outage, term lock, capacity, holds, payment)");
        out.println(" 9) Register and CRASH the process after a committed step");
        out.println(" 0) Exit");
    }

    private void warnAboutInterrupted() {
        List<Registration> interrupted = admin.interrupted();
        if (interrupted.isEmpty()) {
            return;
        }
        out.println();
        out.println("!!  Found " + interrupted.size() + " interrupted registration(s) on disk:");
        interrupted.forEach(item -> out.println("      " + item));
        out.println("!!  Use option 6 to resume or compensate them.");
    }

    private void register() {
        attempt(ask("Student id (e.g. S1)"), ask("Course code (e.g. SE310)"));
    }

    private void attempt(String studentId, String courseCode) {
        out.println("->  " + studentId + " / " + courseCode + ": A eligibility -> B seating -> C billing + transcript");
        try {
            Registration result = registration.register(studentId, courseCode);
            out.println("OK  " + result);
            printSnapshot(result.id());
        } catch (RegistrationRejectedException e) {
            out.println("REJECTED  " + e.getMessage());
            out.println("    nothing was committed downstream, so there is nothing to compensate");
            printSnapshot(e.registration().id());
        } catch (RegistrationFailedException e) {
            out.println("FAILED    " + e.getMessage());
            out.println("    compensation, in the order it was executed:");
            e.compensations().forEach(step -> out.println("      " + step));
            printSnapshot(e.registration().id());
        } catch (InvalidRegistrationException e) {
            out.println("INVALID   " + e.getMessage() + " (nothing was persisted)");
        }
    }

    private void scenarios() {
        out.println(" 1) Happy path                    S1 -> SE310");
        out.println(" 2) Rejected at A: financial hold S2 -> HIST100");
        out.println(" 3) Rejected at A: prerequisite   S4 -> CS201");
        out.println(" 4) B fails after A               S1 -> CS301   (section full)       => undo A");
        out.println(" 5) C fails after A and B         S3 -> MATH201 (credit limit)       => undo B, then A");
        out.println(" 6) C fails after its invoice     S1 -> MATH201 (term locked)        => undo C, B, then A");
        out.println(" 7) Invalid input                 S99 -> CS101");
        switch (ask("Scenario")) {
            case "1" -> attempt("S1", "SE310");
            case "2" -> attempt("S2", "HIST100");
            case "3" -> attempt("S4", "CS201");
            case "4" -> attempt("S1", "CS301");
            case "5" -> attempt("S3", "MATH201");
            case "6" -> {
                admin.setTermOpen(DemoData.TERM, false);
                try {
                    attempt("S1", "MATH201");
                } finally {
                    admin.setTermOpen(DemoData.TERM, true);
                }
            }
            case "7" -> attempt("S99", "CS101");
            default -> out.println("Unknown scenario");
        }
    }

    private void showCatalog() {
        out.println("Students (subsystem A):");
        registration.students().forEach(line -> out.println("  " + line));
        out.println("Courses (subsystem B):");
        registration.catalog().forEach(line -> out.println("  " + line));
    }

    private void inspect() {
        printSnapshot(askId());
    }

    private void recover() {
        List<Registration> interrupted = admin.interrupted();
        if (interrupted.isEmpty()) {
            out.println("No interrupted registrations on disk.");
            return;
        }
        for (Registration item : interrupted) {
            out.println("Interrupted: " + item);
            if (item.status() == Registration.Status.COMPENSATING) {
                out.println("    compensation was already under way; finishing it");
                print(admin.compensate(item.id(), item.reason()));
                continue;
            }
            switch (ask("[r]esume forward, [c]ompensate back, [s]kip")) {
                case "r" -> resume(item);
                case "c" -> print(admin.compensate(item.id(), "Abandoned after crash"));
                default -> out.println("    skipped");
            }
        }
    }

    private void resume(Registration item) {
        try {
            Registration result = registration.resume(item.id());
            out.println("OK  " + result);
            printSnapshot(result.id());
        } catch (RegistrationFailedException e) {
            out.println("FAILED    " + e.getMessage());
            e.compensations().forEach(step -> out.println("      " + step));
        }
    }

    private void compensateManually() {
        long id = askId();
        print(admin.compensate(id, ask("Reason")));
        printSnapshot(id);
    }

    private void failureSwitches() {
        out.println(" 1) Billing system is " + (admin.isBillingOnline() ? "ONLINE" : "OFFLINE") + "  -> toggle");
        out.println(" 2) Transcript for " + DemoData.TERM + " is " + (admin.isTermOpen(DemoData.TERM) ? "OPEN" : "LOCKED")
                + "  -> toggle");
        out.println(" 3) Change a section's capacity");
        out.println(" 4) Place a financial hold on a student");
        out.println(" 5) Lift a financial hold");
        out.println(" 6) Pay the invoice of a registration");
        switch (ask("Switch")) {
            case "1" -> admin.setBillingOnline(!admin.isBillingOnline());
            case "2" -> admin.setTermOpen(DemoData.TERM, !admin.isTermOpen(DemoData.TERM));
            case "3" -> admin.setSectionCapacity(ask("Course code").toUpperCase(), Integer.parseInt(ask("New capacity")));
            case "4" -> admin.placeFinancialHold(ask("Student id").toUpperCase(), ask("Reason"));
            case "5" -> admin.liftFinancialHold(ask("Student id").toUpperCase());
            case "6" -> out.println("Paid: " + admin.payInvoice(askId()));
            default -> {
                out.println("Unknown switch");
                return;
            }
        }
        out.println("Saved to disk.");
    }

    private void registerAndCrash() {
        String studentId = ask("Student id (e.g. S1)");
        String courseCode = ask("Course code (e.g. CS201)");
        String point = ask("Crash after [1] A committed (PENDING_B) or [2] B committed (PENDING_C)");
        crash.armedAt = point.equals("1") ? Registration.Status.PENDING_B : Registration.Status.PENDING_C;
        try {
            attempt(studentId, courseCode);
        } finally {
            crash.armedAt = null;
        }
        out.println("    the chain ended before the crash point, so no crash happened");
    }

    private void printSnapshot(long registrationId) {
        out.println("    persisted now:");
        admin.inspect(registrationId).lines().forEach(line -> out.println("      " + line));
    }

    private void print(List<String> lines) {
        if (lines.isEmpty()) {
            out.println("    nothing to undo");
        }
        lines.forEach(line -> out.println("    " + line));
    }

    private long askId() {
        String raw = ask("Registration id (e.g. 3 or REG-3)").toUpperCase().replace("REG-", "");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new InvalidRegistrationException("Not a registration id: " + raw);
        }
    }

    private String ask(String prompt) {
        out.print(prompt + ": ");
        out.flush();
        if (!in.hasNextLine()) {
            throw new EndOfInput();
        }
        return in.nextLine().trim();
    }
}
