package com.example.library.it;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the {@link BookSearchService} domain port against the real
 * Open Library API.
 * <p>
 * The {@code %test.} profile override of {@code openlibrary.base-url} points the
 * adapter at the WireMock dev service, so this test uses {@link RealOpenLibraryProfile}
 * to restore {@code https://openlibrary.org/} and exercise the real service instead.
 */
@QuarkusTest
@TestProfile(BookSearchServiceTest.RealOpenLibraryProfile.class)
public class BookSearchServiceTest {

    @Inject
    BookSearchService bookSearchService;

    @Test
    public void searchEffectiveJavaIsbnReturnsBookInformation() {
        BookInformation result = bookSearchService.search(new Isbn("9780134685991"));

        assertThat(result.title()).isEqualTo("Effective Java");
    }

    @Test
    public void searchUnknownIsbnThrowsBookNotFoundException() {
        // 978-0-99999999-8 is a checksum-valid ISBN in an unallocated range:
        // Open Library returns 404, which the adapter maps to BookNotFoundException.
        assertThatThrownBy(() -> bookSearchService.search(new Isbn("9780999999998")))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("9780999999998");
    }

    /** Restores the real Open Library base URL, overriding the {@code %test.} WireMock value. */
    public static class RealOpenLibraryProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("openlibrary.base-url", "https://openlibrary.org/");
        }
    }
}
