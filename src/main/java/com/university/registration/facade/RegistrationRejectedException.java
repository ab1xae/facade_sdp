package com.university.registration.facade;

public class RegistrationRejectedException extends RegistrationException {

    private final Registration registration;

    public RegistrationRejectedException(Registration registration) {
        super(registration.reference() + " rejected at stage A: " + registration.reason());
        this.registration = registration;
    }

    public Registration registration() {
        return registration;
    }
}
