package com.university.registration.app;

import com.university.registration.eligibility.EligibilityRepository;
import com.university.registration.eligibility.EligibilityService;
import com.university.registration.eligibility.FinancialHoldChecker;
import com.university.registration.eligibility.PrerequisiteChecker;
import com.university.registration.facade.RegistrarAdminFacade;
import com.university.registration.facade.RegistrationFacade;
import com.university.registration.facade.RegistrationLog;
import com.university.registration.facade.StepListener;
import com.university.registration.records.BillingSystem;
import com.university.registration.records.RecordsRepository;
import com.university.registration.records.TranscriptService;
import com.university.registration.records.TuitionCalculator;
import com.university.registration.seating.SeatAllocator;
import com.university.registration.seating.SeatingRepository;
import com.university.registration.seating.SeatingService;
import com.university.registration.seating.WaitlistManager;

import java.nio.file.Path;

public final class Registrar {

    private final RegistrationFacade registration;
    private final RegistrarAdminFacade admin;

    private Registrar(RegistrationFacade registration, RegistrarAdminFacade admin) {
        this.registration = registration;
        this.admin = admin;
    }

    public static Registrar open(Path dataDir) {
        return open(dataDir, StepListener.NONE);
    }

    public static Registrar open(Path dataDir, StepListener listener) {
        EligibilityService eligibility = new EligibilityService(new EligibilityRepository(dataDir),
                new PrerequisiteChecker(), new FinancialHoldChecker());
        SeatingRepository seatingRepository = new SeatingRepository(dataDir);
        SeatingService seating = new SeatingService(seatingRepository, new SeatAllocator(),
                new WaitlistManager(seatingRepository));
        RecordsRepository recordsRepository = new RecordsRepository(dataDir);
        BillingSystem billing = new BillingSystem(recordsRepository, new TuitionCalculator());
        TranscriptService transcript = new TranscriptService(recordsRepository);
        RegistrationLog log = new RegistrationLog(dataDir);
        return new Registrar(
                new RegistrationFacade(log, eligibility, seating, billing, transcript, listener),
                new RegistrarAdminFacade(log, eligibility, seating, billing, transcript));
    }

    public RegistrationFacade registration() {
        return registration;
    }

    public RegistrarAdminFacade admin() {
        return admin;
    }
}
