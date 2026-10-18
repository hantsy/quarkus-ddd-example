package com.example.library.lending.application;

import com.example.library.shared.UseCase;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanId;
import com.example.library.lending.domain.LoanRepository;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@UseCase
public class ReturnBookUseCase {
    private static final Logger LOGGER = Logger.getLogger(ReturnBookUseCase.class.getName());
    private final LoanRepository loanRepository;
    private final Event<LoanClosed> loanClosedEvent;
    private final Clock clock;

    @Inject
    public ReturnBookUseCase(LoanRepository loanRepository,
                             Event<LoanClosed> loanClosedEvent,
                             Clock clock) {
        this.loanRepository = loanRepository;
        this.loanClosedEvent = loanClosedEvent;
        this.clock = clock;
    }

    public void execute(LoanId loanId) {
        Loan loan = loanRepository.findByIdOrThrow(loanId);
        loan.returned(LocalDateTime.now(clock));
        loanRepository.save(loan);

        LOGGER.log(Level.INFO, "firing returned event for loan with id = " + loanId);
        loanClosedEvent.fire(new LoanClosed(loan.copyId()));
    }
}
