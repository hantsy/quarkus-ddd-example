# Applying DDD with Quarkus

This post is for Java developers who want to see how tactical Domain-Driven
Design (DDD) fits into a Quarkus application. It follows the library sample
from domain objects through persistence, use cases, events, and tests. The
library's requirements and story are covered in the
[Jakarta EE DDD article](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md).

## Strategic design

The design separates Cataloging, which owns books and copies, from Lending,
which owns loans and overdue fees. Lending refers to a copy by its identifier
and publishes loan events so Cataloging can update copy availability without
sharing its entity model. This post focuses on implementing that boundary
with Quarkus CDI's in-process, synchronous events; see the linked article for
the domain story, context map, and subdomain rationale.

## Model catalog and lending separately

The catalog context owns JPA entities `Book` and `Copy`; lending owns the
`Loan` entity. Their embedded ID value objects keep book, copy, loan, and user
identifiers distinct in APIs. `Isbn` validates the ISBN at construction, while
`BarCode` represents the physical copy's barcode.

`Copy` owns its available state and methods for changing it. `Loan` owns
creation time, due date, return time, and overdue fee. Its default constructor
sets a 30-day term; `returned()` computes a flat fee from the overdue day
count. The fee schedule is represented by the `OverdueFee` enum.

The sample's entities include JPA `@Version` fields, but its integration tests
don't exercise concurrent rentals or define an explicit locking policy.
`RegisterBookCopyUseCase` also saves a copy from a `BookId` without checking
that the book exists.

## Declare Jakarta Data repositories

The repository interfaces live beside their domain types. Quarkus Data
Hibernate generates their implementations at compile time:

```java
@Repository
@Transactional
public interface BookRepository extends CrudRepository<Book, BookId> {
    @Query("select count(*) > 0 from Book where isbn = :isbn")
    boolean existsByIsbn(Isbn isbn);
}
```

`LoanRepository.isAvailable()` queries for the absence of an open loan for a
copy. It supports the lending precondition, but a check followed by a save
doesn't by itself prevent two concurrent transactions from passing that check.
The sample doesn't claim race-free availability.

The Maven compiler configures Quarkus's `quarkus-data-processor`, and the
project uses the `quarkus-data-hibernate` extension. This replaces
handwritten repository implementations with Jakarta Data interfaces and
generated code. See the [Quarkus Data Hibernate guide](https://quarkus.io/guides/quarkus-data-hibernate)
for extension setup and supported features.

## Put orchestration in use cases

The `@UseCase` stereotype combines CDI application scope, `@Transactional`,
and the sample's `@Logged` interceptor. `AddBookToCatalogUseCase` checks for a
duplicate ISBN, resolves a title through `BookSearchService`, and saves a
`Book`. `RentBookUseCase` checks availability, reads the injected `Clock`,
saves a `Loan`, and fires `LoanCreated`. `ReturnBookUseCase` updates the loan
and fires `LoanClosed`.

The application services orchestrate repositories and domain behavior. They
don't make the catalog context depend directly on lending's `Copy` entity.

## React to local CDI events

The catalog's `DomainEventListener` observes `LoanCreated` and `LoanClosed`.
It translates the lending copy ID to the catalog ID type, loads the `Copy`,
updates its availability, and saves it. Quarkus CDI events are synchronous by
default; the observer runs during `Event.fire()`. The integration test checks
the copy state after the use case returns.

This keeps the contexts in one application and uses in-process communication.
It is not a durable event log or a broker-based integration boundary. The
`LibraryTest` verifies sequential rent/return behavior and an overdue fee; its
second-rental check uses `Exception.class` and doesn't assert the exact domain
exception. It also doesn't test concurrent rentals.

## Isolate the external book-search service

`BookSearchService` is a catalog-domain port. `OpenLibraryBookSearchService`
implements it using a JAX-RS client, maps the Open Library response to
`BookInformation`, follows redirects, and maps not-found, upstream-status, and
network failures into domain exceptions.

`OpenLibraryBookSearchServiceTest` uses WireMock to test successful redirect,
not-found, upstream error, and network-failure cases without external
network access. `BookSearchServiceTest` calls the real Open Library API and
therefore depends on network and provider availability.

## Enforce package boundaries

The catalog context has application, domain, and infrastructure packages;
lending has application and domain packages. The sample has no presentation
layer.
`ArchitectureTest` checks dependency direction, shared-kernel independence,
context isolation, the event-listener exception, and repository placement.
It excludes `CopyAvailabilityValidator` from the framework-independence rule
because that domain-package service is CDI-injected.

The project targets Java 25 and Quarkus 3.40.1. Its in-memory H2 configuration
is for the sample's tests, not a production database setup. Build and run the
tests with:

```shell
./mvnw clean package
```

Start Quarkus in development mode with:

```shell
./mvnw quarkus:dev
```

## Further reading

- [Quarkus Data Hibernate guide](https://quarkus.io/guides/quarkus-data-hibernate)
- [Quarkus CDI guide](https://quarkus.io/guides/cdi)
- [Jakarta EE DDD article](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md)
- [Spring I/O talk: Implementing Domain-Driven Design with Spring](https://www.youtube.com/watch?v=VGhg6Tfxb60)

The [Helidon](https://github.com/hantsy/helidon-ddd-example),
[Spring Java](https://github.com/hantsy/spring-ddd-example), and
[Spring Kotlin](https://github.com/hantsy/spring-ddd-kotlin-example)
repositories implement the same example with different persistence and event
stacks.
