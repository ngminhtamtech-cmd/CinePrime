# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read first

- **AGENTS.md** holds the binding project rules (Vietnamese). Follow it. In short: Java 25 / Spring Boot MVC / Pug4J / JPA / MySQL / Flyway / plain JS only. Do not add a frontend framework, Redis, microservices, `tools/`, Python scripts, or duplicate docs.
- **Plan.md** is the spec and the source of truth for business rules and stack. It also holds the milestone checklist and the Galaxy reference log, which you must update when you survey public Galaxy UI.
- **UI-Template.md** is the source of truth for design tokens. Its Thymeleaf/React snippets are reference only and are not the chosen stack.
- **README.MD** documents how to run the app and its verified status. Keep README and the Plan checklist limited to results you actually verified. Do not mark MoMo, SMTP, real-device camera or deploy as done without real configuration and testing. Deploy (Vercel later) is deferred; test locally first.

## Commands (Windows / PowerShell)

Set `JAVA_HOME` to a real JDK 25, not the Oracle javapath shim. Configuration comes only from environment variables: see the README "Cấu hình" table and `src/main/resources/application.yml`.

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/cineprime?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USER='cineprime'; $env:DB_PASSWORD='...'
$env:DEMO_SEED='true'; $env:DEMO_PASSWORD='...'   # optional demo data, password >= 10 chars
$env:TEMPLATE_CACHE='false'                        # see .pug edits without restart
.\mvnw.cmd -B -ntp package                         # builds target\cineprime-1.0.0.jar (runs tests)
java -jar target\cineprime-1.0.0.jar
```

The user normally runs the app from an IntelliJ run configuration named **CinePrime Local** on port 8080. Do not stop that process or touch its `cineprime` database. Use the separate `cineprime_test` schema and another port, such as 8081, for anything you verify.

Tests:

```powershell
$env:TEST_DB_URL='jdbc:mysql://localhost:3306/cineprime_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:TEST_DB_USER='cineprime'; $env:TEST_DB_PASSWORD='...'
.\mvnw.cmd -B -ntp test                                         # full suite
.\mvnw.cmd -B -ntp test -Dtest=ShowtimeExcelTest                # one class
.\mvnw.cmd -B -ntp test -Dtest='BookingIntegrationTest#method'  # one method
```

- Without a `TEST_DB_URL` that ends in `/cineprime_test`, `BookingIntegrationTest` (the MySQL/HTTP suite) is **silently skipped** through `@EnabledIfEnvironmentVariable`, and only unit tests run. Before you claim a full pass, check `target/surefire-reports` for 0 skipped.
- The integration suite wipes the test schema's tables before each case. It uses a `MutableClock` bean to control time, plus test MoMo keys, and turns off jobs, mail and seed.
- Never use H2. Concurrency claims (seat, voucher, check-in races) must be proven on MySQL.

## Architecture

There is one Spring Boot app packaged as an executable JAR. Packages under `vn.cineprime` are split by business area:

- `domain`: `Domain.java` defines **all** JPA entities as public-field static nested classes. `Store.java` is the single generic repository, with `get`, `lock` (PESSIMISTIC_WRITE plus refresh), queries and more. There are no Spring Data repositories. `DemoData` seeds data when `DEMO_SEED=true`. `MovieLifecycle` derives Sắp chiếu/Đang chiếu (upcoming/now showing) from the release date in Asia/Ho_Chi_Minh time when data is read.
- `booking`: seat holds, combos and vouchers, and orders. `payment/MomoService`: MoMo captureWallet create, IPN and query, with HMAC verification. `staff`: QR/manual check-in scoped to the staff member's cinema. `notification`: email outbox, reminders and scheduled `Workers`. `media`: upload validation, served under `/media`, and ticket QR/PDF (PDFBox with the bundled Noto Sans). `auth`: `SecurityConfig`, accounts and reset tokens. `web`: public pages (`Pages`, `Catalog`), `PageAdvice` and `PugConfig`.
- `admin`: CRUD, reports/CSV, room seat-map setup (`RoomSetup`), pricing, and the weekly showtime scheduler (`ShowtimeBatches`/`ShowtimeRules`/`ShowtimeExcel`, which handles Excel template v3 and still parses v1/v2). It exposes JSON APIs under `/api/admin/...`.
- Views are `.pug` files in `src/main/resources/templates`, with `layout`/`mixins` and `admin/layout`. They use JEXL expressions, not JavaScript or Node. `PugConfig` registers the Pug view resolver explicitly because Boot's default `viewResolver` bean would make spring-pug4j's auto-config back off. Client behavior is plain JS in `static/js` (ZXing vendored for the camera).
- Rendering is server-side HTML. JSON endpoints exist only for the seat map/holds, combo/voucher changes, payment status/IPN, check-in lookup, notifications and the admin scheduler.

Code style is dense: long single-line methods, public entity fields, and Vietnamese user-facing messages. Match the surrounding style.

## Invariants that must hold

- **Schema**: Flyway migrations live in `db/migration` (`V{n}__*.sql`). Hibernate is set to `ddl-auto: validate`. Never edit an applied migration; add a new one.
- **Transactions**: services own aggregate transactions. Lock with `Store.lock` in a fixed order. Every operation is all-or-nothing on MySQL READ COMMITTED. Seat inventory is unique on (showtime, seat), and QR codes, transactions, the points ledger and voucher usage have uniqueness constraints.
- **Holds**: 5 minutes, enforced by the server. A hold is never extended by reload, combo, voucher or payment changes. A hold is created only if at least 5 minutes remain before sales close (15 minutes before start). One active hold per user, 8 physical seats at most, and couple seats are sold as a pair.
- **Money**: integer VND, computed only on the backend. Snapshot prices, names and discounts on the order.
- **Payments**: callbacks (IPN/query) must be idempotent and must verify signature, merchant, order and amount. A success that arrives after the hold expired is recorded and flagged for reconciliation, and no ticket is issued. A redirect only reads status. There is no fake-success mode.
- **Check-in**: idempotent, one use per QR, allowed from 60 minutes before to 30 minutes after showtime, and only at the staff member's cinema.
- **Security**: role and ownership checks happen in the backend. Use sessions, CSRF and password hashing. Locking an account or changing its role invalidates existing sessions through `authVersion`. Keep secrets out of the repo and out of logs.
- **Time**: business logic uses the Asia/Ho_Chi_Minh zone, and the DB/JDBC layer uses UTC. Inject `Clock`; do not call `now()` directly.
- **Uploads**: runtime data lives under `UPLOAD_DIR` (default `./uploads`) and is never written into the source tree or JAR.
- **Verification** of Pug UTF-8 must be done from the executable JAR, on desktop and mobile, along with Vietnamese PDF output.
