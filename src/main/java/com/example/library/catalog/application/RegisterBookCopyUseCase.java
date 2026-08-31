package com.example.library.catalog.application;

import com.example.library.catalog.domain.BarCode;
import com.example.library.catalog.domain.BookId;
import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.common.UseCase;
import jakarta.inject.Inject;

@UseCase
public class RegisterBookCopyUseCase {
    private final CopyRepository copyRepository;

    @Inject
    public RegisterBookCopyUseCase(CopyRepository copyRepository) {
        this.copyRepository = copyRepository;
    }

    public void execute(BookId bookId, BarCode barCode) {
        copyRepository.save(new Copy(bookId, barCode));
    }
}
