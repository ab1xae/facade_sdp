package com.university.registration.facade;

public class InvalidRegistrationException extends RegistrationException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
