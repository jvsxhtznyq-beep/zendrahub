package com.zendrahub.evolve.core.persist;

/** A save file that parsed as JSON but does not describe a valid world. */
public final class SaveFormatException extends RuntimeException {
    public SaveFormatException(String message) {
        super(message);
    }

    public SaveFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
