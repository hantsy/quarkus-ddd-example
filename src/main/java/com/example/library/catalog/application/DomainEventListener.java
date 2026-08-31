package com.example.library.catalog.application;

import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyId;
import com.example.library.catalog.domain.CopyNotFoundException;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanCreated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DomainEventListener {
    private static final Logger LOGGER = Logger.getLogger(DomainEventListener.class.getName());
    private final CopyRepository copyRepository;

    @Inject
    public DomainEventListener(CopyRepository copyRepository) {
        this.copyRepository = copyRepository;
    }

    public void onLoanCreated(@Observes LoanCreated event) {
        LOGGER.log(Level.INFO, "handling LoanCreated:{0}", new Object[]{event});
        var copyId = new CopyId(event.copyId().id());
        Copy copy = copyRepository.findById(copyId).orElseThrow(() -> new CopyNotFoundException(copyId));
        copy.makeUnavailable();
        copyRepository.save(copy);
    }

    public void onLoanClosed(@Observes LoanClosed event) {
        LOGGER.log(Level.INFO, "handling LoanClosed:{0}", new Object[]{event});
        var copyId = new CopyId(event.copyId().id());
        Copy copy = copyRepository.findById(copyId).orElseThrow(() -> new CopyNotFoundException(copyId));
        copy.makeAvailable();
        copyRepository.save(copy);
    }
}
