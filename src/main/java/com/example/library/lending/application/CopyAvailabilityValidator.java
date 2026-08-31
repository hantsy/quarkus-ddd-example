package com.example.library.lending.application;

import com.example.library.lending.domain.CopyId;
import com.example.library.lending.domain.CopyNotAvailableException;
import com.example.library.lending.domain.LoanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CopyAvailabilityValidator {
    private final LoanRepository loanRepository;

    @Inject
    public CopyAvailabilityValidator(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    public void checkAvailable(CopyId copyId) {
        if (!loanRepository.isAvailable(copyId)) {
            throw new CopyNotAvailableException(copyId);
        }
    }
}
