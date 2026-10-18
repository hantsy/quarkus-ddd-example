package com.example.library.lending.domain;

import com.example.library.shared.DomainException;

/**
 * Thrown when a copy cannot be rented because it is already on loan.
 */
public class CopyNotAvailableException extends DomainException {

    public CopyNotAvailableException(CopyId copyId) {
        super("copy with id = " + copyId + " is not available");
    }
}
