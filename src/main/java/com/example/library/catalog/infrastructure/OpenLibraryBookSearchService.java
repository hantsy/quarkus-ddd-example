package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Open Library adapter implementing the {@link BookSearchService} domain port,
 * backed by the JAX-RS client. The Open Library base URL is configurable via the
 * {@code openlibrary.base-url} property (see {@code application.properties}).
 */
@ApplicationScoped
public class OpenLibraryBookSearchService implements BookSearchService {
    private static final Logger LOGGER = Logger.getLogger(OpenLibraryBookSearchService.class.getName());

    /** Maximum number of HTTP redirects to follow before giving up. */
    private static final int MAX_REDIRECTS = 5;

    private final Client client;
    private final String baseUrl;

    @Inject
    public OpenLibraryBookSearchService(@ConfigProperty(name = "openlibrary.base-url") String baseUrl) {
        this.client = ClientBuilder.newClient();
        this.baseUrl = baseUrl;
    }

    public BookInformation search(Isbn isbn) {
        var targetUri = UriBuilder
                .fromUri(baseUrl + "isbn/{isbn}.json")
                .build(isbn.value());
        try (var response = getFollowingRedirects(targetUri)) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new BookNotFoundException(isbn);
            }
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                LOGGER.log(Level.WARNING, "OpenLibrary returned unexpected status {0} for isbn {1}",
                        new Object[]{response.getStatus(), isbn.value()});
                throw new BookSearchException(
                        "failed to search book, upstream returned status " + response.getStatus());
            }
            var result = response.readEntity(OpenLibraryIsbnSearchResult.class);
            LOGGER.log(Level.FINEST, "Book search result: {0}", result);
            return new BookInformation(result.title());
        } catch (ProcessingException e) {
            LOGGER.log(Level.SEVERE, "network error searching isbn {0}: {1}",
                    new Object[]{isbn.value(), e.getMessage()});
            throw new BookSearchException("failed to search book for isbn: " + isbn.value(), e);
        }
    }

    /**
     * Performs the GET, transparently following HTTP redirects. Open Library's
     * {@code /isbn/{isbn}.json} endpoint answers with a 302 to the canonical
     * {@code /books/{key}.json} location, which the JAX-RS client does not follow
     * on its own. The {@code requestUri} is kept so a relative {@code Location}
     * header can be resolved against it.
     */
    private Response getFollowingRedirects(URI requestUri) {
        var response = this.client.target(requestUri)
                .request().accept(MediaType.APPLICATION_JSON_TYPE).get();
        int redirects = 0;
        while (Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.REDIRECTION
                && redirects < MAX_REDIRECTS) {
            var location = response.getLocation();
            if (location == null) {
                return response;
            }
            response.close();
            redirects++;
            response = this.client.target(requestUri.resolve(location))
                    .request().accept(MediaType.APPLICATION_JSON_TYPE).get();
        }
        return response;
    }
}
