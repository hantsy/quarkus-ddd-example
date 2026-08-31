package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.*;
import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenLibraryBookSearchService}, the infrastructure adapter
 * behind the {@link BookSearchService} domain port.
 * <p>
 * The Open Library endpoint is mocked with the Quarkiverse WireMock dev service:
 * {@code @ConnectWireMock} starts a WireMock server and injects a {@link WireMock}
 * client, and the {@code %test.} profile override of {@code openlibrary.base-url}
 * points it at the mock server, so the adapter exercises the real JAX-RS client
 * stack against stubbed success and failure responses, with no external network access.
 */
@QuarkusTest
@ConnectWireMock
@TestProfile(OpenLibraryBookSearchServiceTest.MockOpenLibraryProfile.class)
class OpenLibraryBookSearchServiceTest {

    /**
     * A valid ISBN-13 (checksum correct) that is not allocated to any book.
     */
    private static final String UNKNOWN_ISBN = "9780000000002";

    private static final String KNOWN_ISBN = "9780134685991";

    /**
     * The adapter under test, pointed at the WireMock server via {@code openlibrary.base-url}.
     */
    @Inject
    BookSearchService service;

    @BeforeEach
    void resetWireMock() {
        reset();
    }

    @Test
    void searchWithKnownIsbnShouldReturnBookInformation() {
        stubFor(get(urlEqualTo("/isbn/" + KNOWN_ISBN + ".json"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "/books/OL31838212M.json")));

        stubFor(get(urlEqualTo("/books/OL31838212M.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "title": "Effective Java",
                                  "publishers": ["Addison-Wesley"],
                                  "isbn_13": ["9780134685991"],
                                  "revisions": 1
                                }
                                """)));

        BookInformation result = service.search(new Isbn(KNOWN_ISBN));

        assertThat(result.title()).isEqualTo("Effective Java");

        verify(getRequestedFor(urlEqualTo("/isbn/" + KNOWN_ISBN + ".json"))
                .withHeader("Accept", equalTo("application/json")));
        verify(getRequestedFor(urlEqualTo("/books/OL31838212M.json"))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void searchWithUnknownIsbnShouldThrowBookNotFoundExceptionWhenUpstreamReturns404() {
        stubFor(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(UNKNOWN_ISBN);

        verify(getRequestedFor(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json")));
    }

    @Test
    void searchShouldThrowBookSearchExceptionWhenUpstreamReturnsErrorStatus() {
        stubFor(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookSearchException.class)
                .hasMessageContaining("500");
    }

    @Test
    void searchShouldThrowBookSearchExceptionWhenNetworkFails() {
        stubFor(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookSearchException.class)
                .hasMessageContaining(UNKNOWN_ISBN)
                .hasCauseInstanceOf(ProcessingException.class);
    }

    /**
     * Restores the real Open Library base URL, overriding the {@code %test.} WireMock value.
     */
    public static class MockOpenLibraryProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "mock";
        }
    }
}
