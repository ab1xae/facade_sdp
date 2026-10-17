package com.university.registration.storage;

public class StorageException extends RuntimeException {

    public StorageException(Throwable cause) {
        super("Persistent store failure: " + cause.getMessage(), cause);
    }
}
