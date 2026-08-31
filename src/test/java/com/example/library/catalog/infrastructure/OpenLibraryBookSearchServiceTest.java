package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
class OpenLibraryBookSearchServiceTest {

    /** A valid ISBN-13 (checksum correct) that is not allocated to any book. */
    private static final String UNKNOWN_ISBN = "9780000000002";

    private static final String KNOWN_ISBN = "9780134685991";

    /** The adapter under test, pointed at the WireMock server via {@code openlibrary.base-url}. */
    @Inject
    BookSearchService service;

    /** WireMock client, injected automatically by {@code @ConnectWireMock}. */
    WireMock wiremock;

    @BeforeEach
    void resetWireMock() {
        wiremock.resetMappings();
        wiremock.resetRequests();
    }

    @Test
    void searchWithKnownIsbnShouldReturnBookInformation() {
        wiremock.register(get(urlEqualTo("/isbn/" + KNOWN_ISBN + ".json"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "/books/OL31838212M.json")));

        wiremock.register(get(urlEqualTo("/books/OL31838212M.json"))
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

        wiremock.verifyThat(getRequestedFor(urlEqualTo("/isbn/" + KNOWN_ISBN + ".json"))
                .withHeader("Accept", equalTo("application/json")));
        wiremock.verifyThat(getRequestedFor(urlEqualTo("/books/OL31838212M.json"))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void searchWithUnknownIsbnShouldThrowBookNotFoundExceptionWhenUpstreamReturns404() {
        wiremock.register(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(UNKNOWN_ISBN);

        wiremock.verifyThat(getRequestedFor(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json")));
    }

    @Test
    void searchShouldThrowBookSearchExceptionWhenUpstreamReturnsErrorStatus() {
        wiremock.register(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookSearchException.class)
                .hasMessageContaining("500");
    }

    @Test
    void searchShouldThrowBookSearchExceptionWhenNetworkFails() {
        wiremock.register(get(urlEqualTo("/isbn/" + UNKNOWN_ISBN + ".json"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.search(new Isbn(UNKNOWN_ISBN)))
                .isInstanceOf(BookSearchException.class)
                .hasMessageContaining(UNKNOWN_ISBN)
                .hasCauseInstanceOf(ProcessingException.class);
    }
}
