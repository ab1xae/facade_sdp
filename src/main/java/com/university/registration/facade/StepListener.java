package com.university.registration.facade;

@FunctionalInterface
public interface StepListener {

    StepListener NONE = registration -> { };

    void committed(Registration registration);
}
