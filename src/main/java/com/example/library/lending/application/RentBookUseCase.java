package com.example.library.lending.application;

import com.example.library.shared.UseCase;
import com.example.library.lending.domain.*;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@UseCase
public class RentBookUseCase {
    private static final Logger LOGGER = Logger.getLogger(RentBookUseCase.class.getName());
    private final LoanRepository loanRepository;
    private final CopyAvailabilityValidator copyAvailabilityValidator;
    private final Event<LoanCreated> loanCreatedEvent;
    private final Clock clock;

    @Inject
    public RentBookUseCase(LoanRepository loanRepository,
                           CopyAvailabilityValidator copyAvailabilityValidator,
                           Event<LoanCreated> loanCreatedEvent,
                           Clock clock) {
        this.loanRepository = loanRepository;
        this.copyAvailabilityValidator = copyAvailabilityValidator;
        this.loanCreatedEvent = loanCreatedEvent;
        this.clock = clock;
    }

    public void execute(CopyId copyId, UserId userId) {
        copyAvailabilityValidator.checkAvailable(copyId);
        var now = LocalDateTime.now(clock);
        loanRepository.save(new Loan(copyId, userId, now));

        LOGGER.log(Level.INFO, "firing LoanCreated with copy id = " + copyId);
        loanCreatedEvent.fire(new LoanCreated(copyId));
    }
}
