package com.example.library.catalog.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;

import java.util.Objects;

@Entity
public class Copy {
    @EmbeddedId
    private CopyId id;
    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "book_id"))
    private BookId bookId;
    @Embedded
    private BarCode barCode;
    private boolean available;

    @Version
    private Long version;

    Copy() {
    }

    public Copy(CopyId copyId, BookId bookId, BarCode barCode) {
        Objects.requireNonNull(copyId, "copyId must not be null");
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(barCode, "barCode must not be null");
        this.id = copyId;
        this.bookId = bookId;
        this.barCode = barCode;
        this.available = true;
    }

    public Copy(BookId bookId, BarCode barCode) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(barCode, "barCode must not be null");
        this.id = new CopyId();
        this.bookId = bookId;
        this.barCode = barCode;
        this.available = true;
    }

    public CopyId id() {
        return this.id;
    }

    public void makeUnavailable() {
        this.available = false;
    }

    public void makeAvailable() {
        this.available = true;
    }

    public boolean isAvailable() {
        return this.available;
    }
}
