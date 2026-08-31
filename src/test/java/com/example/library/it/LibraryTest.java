package com.example.library.it;

import com.example.library.catalog.domain.BarCode;
import com.example.library.catalog.domain.Book;
import com.example.library.catalog.domain.BookRepository;
import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyId;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.catalog.domain.Isbn;
import com.example.library.lending.application.RentBookUseCase;
import com.example.library.lending.application.ReturnBookUseCase;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanId;
import com.example.library.lending.domain.LoanRepository;
import com.example.library.lending.domain.OverdueFee;
import com.example.library.lending.domain.UserId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end integration test of the library use cases, backed by an in-memory
 * H2 database and exercising the cross-context CDI domain events (the catalog
 * observes lending's {@code LoanCreated}/{@code LoanClosed} to keep copy
 * availability in sync, synchronously within the same transaction).
 */
@QuarkusTest
public class LibraryTest {

    @Inject
    BookRepository bookRepository;

    @Inject
    CopyRepository copyRepository;

    @Inject
    LoanRepository loanRepository;

    @Inject
    RentBookUseCase rentBookUseCase;

    @Inject
    ReturnBookUseCase returnBookUseCase;

    private void withTx(Runnable action) {
        QuarkusTransaction.requiringNew().run(action);
    }

    @BeforeEach
    void cleanUp() {
        withTx(() -> {
            loanRepository.deleteAll(loanRepository.findAll().toList());
            copyRepository.deleteAll(copyRepository.findAll().toList());
            bookRepository.deleteAll(bookRepository.findAll().toList());
        });
    }

    @Test
    public void testLibraryCrud() {
        CopyId copyId = new CopyId();

        withTx(() -> {
            // Add a new Book
            Book book = new Book("Effective Java", new Isbn("9780134685991"));
            bookRepository.save(book);

            // Add some copies of the book
            Copy copy1 = new Copy(copyId, book.getId(), new BarCode("BC001"));
            Copy copy2 = new Copy(book.getId(), new BarCode("BC002"));
            copyRepository.save(copy1);
            copyRepository.save(copy2);
        });

        withTx(() -> {
            // verify all copies
            var allCopies = copyRepository.findAll().toList();
            assertThat(allCopies.size()).isEqualTo(2);
        });

        UserId userId = new UserId();
        withTx(() -> {
            // Rent a book
            rentBookUseCase.execute(com.example.library.lending.domain.CopyId.of(copyId.id()), userId);
        });

        withTx(() -> {
            // Verify that the book is NOT available (event handled synchronously)
            var copyOptional = copyRepository.findById(copyId);
            assertThat(copyOptional).isPresent();
            assertThat(copyOptional.get().isAvailable()).isFalse();
        });

        // rent again should throw exception (the use case manages its own transaction)
        assertThrows(Exception.class,
                () -> rentBookUseCase.execute(com.example.library.lending.domain.CopyId.of(copyId.id()), userId));

        withTx(() -> {
            // verify ONLY one loan record
            var allLoans = loanRepository.findAll().toList();
            assertThat(allLoans.size()).isEqualTo(1);

            // Retrieve Loan
            Loan loan = loanRepository.findByIdOrThrow(allLoans.getFirst().id());
            assertThat(loan.copyId().id()).isEqualTo(copyId.id());

            // Return the book
            returnBookUseCase.execute(loan.id());
        });

        withTx(() -> {
            // Verify that the book is now available (event handled synchronously)
            var returnedCopyOptional = copyRepository.findById(copyId);
            assertThat(returnedCopyOptional).isPresent();
            assertThat(returnedCopyOptional.get().isAvailable()).isTrue();
        });
    }

    @Test
    public void testOverdueReturn() {
        CopyId copyId = new CopyId();
        withTx(() -> {
            Book book = new Book("Domain-Driven Design", new Isbn("9780321125217"));
            bookRepository.save(book);
            copyRepository.save(new Copy(copyId, book.getId(), new BarCode("BC003")));
        });

        var overdueLoanIdHolder = new AtomicReference<LoanId>();
        UserId userId = new UserId();
        withTx(() -> {
            // Create a loan with an expected return date 35 days in the past
            var pastDate = LocalDate.now().minusDays(35);
            var loan = new Loan(
                    com.example.library.lending.domain.CopyId.of(copyId.id()),
                    userId,
                    LocalDateTime.now().minusDays(35),
                    pastDate);
            loanRepository.save(loan);
            overdueLoanIdHolder.set(loan.id());

            // Return the book — should trigger overdue fee
            returnBookUseCase.execute(loan.id());
        });

        withTx(() -> {
            var loan = loanRepository.findByIdOrThrow(overdueLoanIdHolder.get());
            assertThat(loan.returnedAt()).isNotNull();
            assertThat(loan.overdueFee()).isEqualTo(OverdueFee.BEYOND_A_MONTH.amount());
        });
    }
}
