# Quarkus DDD Example

A **Library** example demonstrating modular DDD with **Quarkus** (3.39), **Java 25**, **Maven**, and **Quarkus Data Hibernate** for persistence.

It is the Quarkus port of the [Jakarta EE DDD example](https://github.com/hantsy/jakartaee-ddd-example), reusing the same domain model, Jakarta Data repositories, CDI events, and JAX-RS client — all of which Quarkus provides natively.

## Architecture

Two bounded contexts (`catalog`, `lending`) plus a shared kernel (`common`):

```
com.example.library
├── catalog
│   ├── domain        Book, Copy (JPA entities), BookId/CopyId/Isbn/BarCode (VOs),
│   │                 BookRepository, CopyRepository (Jakarta Data), BookSearchService (port)
│   ├── application   AddBookToCatalogUseCase, RegisterBookCopyUseCase, DomainEventListener
│   └── infrastructure OpenLibraryBookSearchService (JAX-RS client)
├── lending
│   ├── domain        Loan (JPA entity), LoanId/CopyId/UserId (VOs), OverdueFee,
│   │                 LoanCreated/LoanClosed (events), LoanRepository
│   └── application   RentBookUseCase, ReturnBookUseCase, CopyAvailabilityValidator
└── common            DomainException, @UseCase stereotype, @Logged interceptor, Clock
```

- **Persistence** — Jakarta Data repositories (`@Repository` + `CrudRepository` + `@Query`), implemented by Quarkus Data Hibernate with compile-time generation (`quarkus-data-hibernate` + the `quarkus-data-processor` annotation processor).
- **Use cases** — CDI `@ApplicationScoped` beans via the `@UseCase` stereotype (`@Transactional` + `@Logged` interceptor).
- **Cross-context events** — CDI `Event<LoanCreated>/Event<LoanClosed>` fired by lending, observed synchronously by `catalog`'s `DomainEventListener` to toggle `Copy.available`.
- **Open Library ISBN search** — a JAX-RS `ClientBuilder` adapter (via `quarkus-rest-client` + `quarkus-rest-client-jackson`).

## Build

```bash
./mvnw clean package          # run all tests and build the runner jar
./mvnw quarkus:dev            # live-reload development mode
```

## Prerequisites

- JDK 25+
- Maven 3.9.11+ (or the Maven wrapper)
