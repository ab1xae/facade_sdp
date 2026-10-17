package com.university.registration.facade;

import java.util.List;

public class RegistrationFailedException extends RegistrationException {

    private final Registration registration;
    private final List<String> compensations;

    public RegistrationFailedException(Registration registration, List<String> compensations) {
        super(registration.reference() + " failed at stage " + registration.failedStage() + ": " + registration.reason());
        this.registration = registration;
        this.compensations = List.copyOf(compensations);
    }

    public Registration registration() {
        return registration;
    }

    public List<String> compensations() {
        return compensations;
    }
}
