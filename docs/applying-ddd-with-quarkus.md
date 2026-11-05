# Applying DDD with Quarkus

Generated repository implementations make the Quarkus version an interesting
counterpoint to the hand-written SQL in the Helidon example. The entities and
bounded contexts remain recognizable, but Quarkus Data Hibernate implements
Jakarta Data contracts at build time. The shared library scenario is described
elsewhere; this post looks at the Quarkus-specific tactical design.

## Strategic design

Cataloging owns books and physical copies; Lending owns loans and fees. Lending
keeps only a copy identifier and publishes loan events, leaving Cataloging to
update its own copy state. Curious how the example arrived at these boundaries?
The [Jakarta EE DDD article](https://github.com/hantsy/jakartaee-ddd-example/blob/master/docs/2026-10-08-applying-ddd-with-jakarta-ee-and-jakarta-data.md)
traces the strategic design; here, we'll see how Quarkus implements the model
without crossing that boundary.

## Part II — Tactical design with Quarkus

Quarkus combines familiar Jakarta APIs with build-time implementation
generation: JPA maps the aggregate roots, Quarkus Data Hibernate implements
the Jakarta Data repositories, and CDI connects the contexts with synchronous
events.

### 1. Entities, value objects, and aggregate roots

#### Which concepts are entities and which are values

The model tracks three entities by identity: `Book` represents a catalog
record, `Copy` a physical item, and `Loan` a lending record. Each is mapped as
a JPA entity and used as an aggregate root for its own state.

The model represents identities and business values with `BookId`, `CopyId`,
`LoanId`, `UserId`, `Isbn`, `BarCode`, and `OverdueFee`. `Isbn` and `BarCode`
are value objects; `OverdueFee` represents the fee schedule. `Copy` stores a
`BookId`, while `Loan` stores a Lending-owned `CopyId` and a `UserId`.

#### Aggregate boundaries

`Copy` refers to its book by `BookId`, and `Loan` refers to the copy and
borrower by identifiers. Neither aggregate embeds another aggregate. This
keeps catalog state separate from lending state.

> Aggregates refer to each other by identity, not by containment.

#### Invariants and their guards

`Copy` changes availability through `makeAvailable()` and
`makeUnavailable()`. `Loan.returned()` records the return and calculates an
overdue fee. `CopyAvailabilityValidator` checks the open-loan rule through
`LoanRepository` before the use case creates a new loan.

`RegisterBookCopyUseCase` accepts a `BookId` but does not check that the book
exists before saving the copy. The sample therefore does not enforce the
book-to-copy association invariant.

`Copy` and `Loan` declare JPA `@Version` fields, but the integration tests do
not exercise simultaneous rentals or establish a concurrency policy. A
repository check followed by a save does not, by itself, prevent concurrent
requests from both passing the availability check.

#### Map the model with JPA

JPA annotations map the domain classes directly. `@EmbeddedId` maps identity,
and `@Embedded` with `@AttributeOverride` maps values to the intended columns.
For example, `Loan` embeds its own ID and uses column overrides for the copy
and borrower IDs:

```java
@Entity
public class Loan {
    @EmbeddedId
    private LoanId loanId;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "copy_id"))
    private CopyId copyId;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "user_id"))
    private UserId userId;
}
```

The classes do not implement jMolecules aggregate interfaces; the aggregate
boundaries come from the domain model and are guarded by package rules.

### 2. Repositories with Jakarta Data

Quarkus Data Hibernate generates repository implementations from Jakarta Data
interfaces at compile time. The contracts live beside their aggregate types:

```java
@Repository
@Transactional
public interface BookRepository extends CrudRepository<Book, BookId> {
    @Query("select count(*) > 0 from Book where isbn = :isbn")
    boolean existsByIsbn(Isbn isbn);
}
```

`LoanRepository` declares a query that checks whether a copy has an open loan.
The application use cases depend on these repository contracts, not on
generated classes or an entity manager. This differs from Helidon Data JDBC:
the Quarkus repository uses Jakarta Data annotations and a query in the
repository interface rather than explicit JDBC statements and row mappers.

### 3. Domain events with CDI

#### Events in this example

Lending publishes two immutable facts: `LoanCreated` and `LoanClosed`. Each
event carries the Lending context's `CopyId`; it does not expose a Cataloging
entity. The use cases inject `Event<LoanCreated>` or `Event<LoanClosed>` and
fire the event after saving the relevant loan.
The loan records overdue fees, but the sample does not publish an
`OverdueFeeCalculated` event.

#### Publish and observe CDI events

Cataloging observes the events in `DomainEventListener` with CDI's
`@Observes`. The listener translates the identifier to Cataloging's `CopyId`,
loads Cataloging's `Copy`, updates its availability, and saves it. CDI
observers run synchronously by default, so the integration test checks the
updated copy when the use case returns.

This is in-process event delivery inside one application. It is not a
message-broker integration or a durable event log. A service split would need
a different delivery and transaction strategy.

### 4. Domain and application services with CDI

Application services coordinate use cases; domain services enforce rules that
depend on more than one aggregate. Quarkus uses CDI for injection and
transactional application services.

#### 4.1 Application services and transactions

`AddBookToCatalogUseCase`, `RegisterBookCopyUseCase`, `RentBookUseCase`, and
`ReturnBookUseCase` coordinate application operations. The shared `@UseCase`
stereotype combines CDI application scope, `@Transactional`, and the sample's
`@Logged` interceptor.

`RentBookUseCase` checks availability, reads the current time from an
injected `Clock`, saves a `Loan`, and fires `LoanCreated`. `ReturnBookUseCase`
records the return and any fee, saves the loan, and fires `LoanClosed`.

#### 4.2 Domain services and cross-aggregate rules

`CopyAvailabilityValidator` queries `LoanRepository` to check for an open
loan. It raises `CopyNotAvailableException` when another loan makes the copy
unavailable. The rule depends on more than one `Loan`, so the application
delegates it to a domain service rather than putting the query in an entity.

As in the other examples, a sequential availability check alone does not
prevent two concurrent transactions from both passing the check.

#### 4.3 Ports and adapters with Quarkus

Cataloging declares `BookSearchService` as a port for ISBN lookup.
`OpenLibraryBookSearchService` implements the port with a JAX-RS client,
translates provider data into `BookInformation`, and maps HTTP and network
failures to domain exceptions. The use case depends on the port rather than
the client or provider response.

### 5. Packages and ArchUnit rules

Cataloging and Lending have separate context packages, each with
`application` and `domain` packages. Cataloging also has an `infrastructure`
package for the external book-search adapter; `shared` contains
cross-cutting types.

`ArchitectureTest` uses ArchUnit to check that:

- domain packages do not depend on application or infrastructure packages;
- domain packages avoid CDI and REST APIs, except for
  `CopyAvailabilityValidator`;
- application packages depend on domain but not on infrastructure;
- `shared` does not depend on either bounded context;
- Lending does not depend on Cataloging;
- only the catalog `DomainEventListener` may depend on Lending; and
- repository contracts reside in domain packages.

The framework-independence rule excludes `CopyAvailabilityValidator`, which
CDI injects. JPA annotations remain on the domain entities, so this test does
not claim that persistence mapping is framework-independent. The test also
excludes generated Quarkus Data repository and metamodel classes from its
imported class set.

## Part III — Verify scenarios with integration tests

`LibraryTest` exercises sequential rent and return behavior, availability
updates, a second-rental rejection, and an overdue fee. The rejection
assertion accepts `Exception`, so it does not verify the precise domain
exception. The tests do not exercise concurrent rentals.

`OpenLibraryBookSearchServiceTest` uses WireMock for provider responses and
failure cases. A separate live test calls the real Open Library API and
depends on network and provider availability.

## Run the sample

Build the project and run its tests with:

```shell
./mvnw clean package
```

To start Quarkus in development mode, use:

```shell
./mvnw quarkus:dev
```

## Summary

Quarkus maps aggregates as JPA entities, generates Jakarta Data repositories
with Quarkus Data Hibernate, and uses synchronous CDI observers for
cross-context events. ArchUnit tests encode the package and dependency rules,
including the explicit listener exception.

### Further reading

- [Quarkus Data Hibernate guide](https://quarkus.io/guides/quarkus-data-hibernate)
- [Quarkus CDI guide](https://quarkus.io/guides/cdi)
- [Helidon DDD example](https://github.com/hantsy/helidon-ddd-example)
- [Spring Boot Java DDD example](https://github.com/hantsy/spring-ddd-example)
- [Spring Boot Kotlin DDD example](https://github.com/hantsy/spring-ddd-kotlin-example)
