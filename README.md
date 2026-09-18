# GitLab Issues API Tests

CRUD and edge-case test suite for the GitLab Issues REST API, targeting **gitlab.com**.

**Stack:** Java 17 · Maven · JUnit 5 · REST Assured · AssertJ · Jackson · Lombok

## Why this stack

| Choice | Reason |
|---|---|
| JUnit 5 over TestNG | Parameterized tests, tags, and parallel execution now cover everything TestNG was chosen for, with a better extension model and tooling. |
| REST Assured for transport only | Assertions use AssertJ against typed models. The `.then().body("title", equalTo(...))` style has no compile-time safety and gives poor failure messages. |
| Records for responses, Lombok for requests | Records give accessors, equals/hashCode and immutability for free. Lombok's `@Builder` is worth it only on request payloads, where most fields are optional. |
| JSON Schema alongside typed models | The models cannot catch a narrowing contract — unknown properties are ignored and a missing field maps to `null`, so a response that dropped `web_url` would still pass every model assertion. |

## Setup

1. Create a personal access token with the **`api`** scope:
   <https://gitlab.com/-/user_settings/personal_access_tokens>

2. Pick a project to test against. It must be one you **own** — issue deletion requires
   Owner or admin rights, and a Maintainer receives `403`. A private project in your own
   personal namespace is ideal.

3. Configure. Environment variables are preferred over `-D` flags: the token stays out of shell
   history, out of the process list, and out of anything you might paste.

   ```bash
   export GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx
   export GITLAB_PROJECT_ID=12345678
   mvn clean test
   ```

   Or, for local development, a git-ignored properties file instead of exporting anything:

   ```bash
   cp src/main/resources/sample.gitlab.properties src/main/resources/gitlab.properties
   # then edit gitlab.properties - it is git-ignored, so it never reaches a commit
   ```

   `Config` resolves every value in the same order regardless of which of these you used: system
   property, then environment variable, then `gitlab.properties` if you created one, then the
   committed `config.properties` (which only ever holds the non-secret `gitlab.baseUri` /
   `gitlab.basePath` defaults — never a token or a project id).

   If no project id is configured, a private scratch project is created for the run and deleted
   on JVM exit. Convenient, but it consumes the project-creation rate limit — prefer a fixed
   project for repeated runs.

Nothing is hardcoded and no token or project identifier is committed. `Config` fails fast with
setup instructions when the token is absent, rather than letting the whole suite report `401`.

> **Never pass a token where it can be recorded.** A PAT with the `api` scope can read, edit and
> delete anything the owner can. If one ends up in shell history, a CI log, a screenshot or a chat
> message, revoke it at <https://gitlab.com/-/user_settings/personal_access_tokens> — rotating is
> seconds, and the alternative is an credential live for its full validity period.


## Reporting

Two layers, deliberately:

**Allure → forensics and trends.** For an API suite the payoff is `AllureAttachmentFilter`, which
attaches the **request and response of every call** to the report. No annotations on any test are
required: when an assertion fails in CI you see the exact URL, query string and JSON that crossed
the wire, instead of only `expected: [42] but was: []`.

```bash
mvn clean test
mvn allure:report                                  # → target/site/allure-maven-plugin/index/html
allure serve target/allure-results                 # or, with the CLI installed
```

The report is a JavaScript app and cannot be opened over `file://` — use `allure serve`, or
`python -m http.server` inside the generated directory.
Or just use any browser and open index.html file.

### Verbose logging

By default only a *failing* status check dumps its request/response
(`enableLoggingOfRequestAndResponseIfValidationFails()` — see
[Credential masking](#credential-masking--two-separate-leak-paths-two-separate-fixes) below), so a
passing run stays quiet. For everything else — confirming what a fixture actually sent, for
instance — the `verbose` profile dumps every request and response and drops the slf4j level to
`debug`:

```bash
mvn test -Pverbose -Dtest=IssueCreateTest
```

`PRIVATE-TOKEN` is masked here too, but *not* for free — `RequestLoggingFilter`'s and
`ResponseLoggingFilter`'s no-arg constructors default `blacklistedHeaders` to an empty set and never
consult `LogConfig`'s blacklist at all, unlike `enableLoggingOfRequestAndResponseIfValidationFails()`.
`GitLabClient.base()` passes the blacklist explicitly to `RequestLoggingFilter`'s constructor
instead. See [Credential masking](#credential-masking--three-separate-leak-paths-three-separate-fixes)
below for the full story and the other two leak paths.

### Tags

| Tag | Meaning |
|---|---|
| `api` | Hits a live GitLab instance. Applied to every subclass of `ApiTest`, so every test carries it. |


## Layout

The reusable API client lives in `src/main`, the suite in `src/test`. JUnit, AssertJ, Allure and the
schema validator are all `test`-scoped, so none of them can be imported by the client.

That boundary is real but not airtight, and it is worth being precise about: Hamcrest arrives on the
`src/main` compile classpath transitively through rest-assured, which is why
`.then().statusCode(...)` is available there. The guarantee is that JUnit and AssertJ cannot leak in,
not that no assertion of any kind can.

```
src/main/java/com/qa/gitlab/          ← the reusable client, no test framework on its classpath
  client/    GitLabClient      transport: base URI, auth header, JSON mapper
             IssuesApi         the endpoint — typed + *Raw variants
             Page              one page plus its pagination headers
             RetryFilter       429 / transient-5xx backoff honouring Retry-After
             ProjectsApi       project lifecycle
             UsersApi          GET /user, so a test can learn its own user id in one call
  model/     Issue, Author, Project              response records
             CreateIssueRequest, UpdateIssueRequest
             IssueQuery                          the full documented filter set
             IssueState, IssueStateFilter, IssueType, IssueScope,
             IssueOrderBy, SortDirection, SearchIn, DueDateFilter, StateEvent, FilterValue
  config/    Config            sysprop → env var → gitlab.properties → config.properties
  support/   Json              the single ObjectMapper (snake_case configured once)

src/main/resources/
  config.properties             committed non-secret defaults: gitlab.baseUri, gitlab.basePath
  sample.gitlab.properties       copy to gitlab.properties (git-ignored) for local token/projectId

src/test/java/com/qa/gitlab/          ← the suite
  testkit/   ApiTest           base fixture + guaranteed per-test teardown
             TestProject       resolves or creates the target project, once per JVM
             TestData          unique-name and boundary-input factories
             Schemas           JSON Schema matchers for the response contract
             Tags              tag names as constants
             AllureAttachmentFilter  request/response attachments, credentials masked
  tests/     IssueCreateTest        IssueListFilterTest   IssuePaginationTest
             IssueReadTest          IssueReadNotFoundTest
             IssueUpdateTest        IssueUpdateNegativeTest
             IssueDeleteTest        IssueDeleteNegativeTest
             IssueSchemaTest        IssueErrorSchemaTest
             IssueBoundaryTest      IssueAuthorizationTest   IssueValidationTest
               ↑ read/update/delete and the schema contract each split their happy path
                 from their error path, so either can be read on its own

src/test/resources/schemas/    issue.json, issue-list.json, error.json
src/test/resources/allure.properties   results land in target/
```

See [COVERAGE.md](COVERAGE.md) for what is covered, what is deliberately not, and why, and
[FINDINGS.md](FINDINGS.md) for API behaviours the suite surfaced. Tests referencing a finding carry
a `FINDING-n` comment, so `grep -rn FINDING- src` locates all of them.

### Reusing the client

```java
IssuesApi issues = new IssuesApi();
Issue seeded = issues.create(projectId, CreateIssueRequest.builder().title("fixture").build());
```


### Teardown 

`ApiTest.givenIssue()` registers each created issue and `@AfterEach` deletes them newest-first.
Cleanup is best-effort and logged, never thrown, so it cannot mask the real failure. The
tracking list is an instance field and JUnit creates one instance per test method, so isolation
needs no synchronization.

The alternative — deleting at the end of each test body — leaks a resource on **every** failing
test, which is how a suite ends up needing a separate cleanup job.

### Filter tests assert absence, not just presence

A filter the server silently ignores returns the unfiltered collection, which still *contains*
the expected issue. Every filter test therefore creates a negative control and asserts it is
**not** in the result. Assertions are scoped to a freshly generated label or search string, so
they hold against a shared project with pre-existing data and under concurrent runs.

### Pagination is tested through headers

Most of the pagination contract lives in `X-Total`, `X-Next-Page` and `Link`, not in the body.
`Page` parses them so they can be asserted — including the two details that trip people up:
GitLab sends `X-Next-Page` as an *empty string* on the last page, and the `Link` header's
comma-separated segments can contain commas inside their own query strings.

### Rate limits are handled, not tolerated

`RetryFilter` retries `429` for any method, and `502/503/504` only for idempotent ones — a
`POST` that timed out may have created the issue, and retrying it would create a second. `500`
is never retried: that is a finding to report, not a transient condition to paper over.

## GitLab API notes worth knowing

- **Issues are addressed by `iid`, not `id`.** `iid` is the per-project internal id used in URLs;
  `id` is global. Mixing them up is the most common mistake against this API.
- **Closing is an update, not a delete** — `PUT …/issues/:iid` with `state_event=close`.
- **`DELETE` needs Owner or admin.** A Maintainer gets `403`.
- **A project you cannot see returns `404`, not `403`.** Hiding existence is correct; a test
  expecting `403` would be asserting a leak.
- **`labels` is a comma-separated string**, and an empty string is how you clear all labels.
- **`Any` / `None`** are case-sensitive magic values for several filters — hence `FilterValue`.
- **`per_page` is clamped at 100**, not rejected, and defaults to 20.
- **`weight`, `epic_id`, and iterations are Premium/Ultimate.** Accepted and silently ignored on
  Free tier, so the suite does not assert on them.

## CI

`.github/workflows/api-tests.yml` runs two jobs:

- **compile** — on every push and pull request, including from forks. The only signal a fork can
  get, since every test needs a token and secrets are not exposed to fork pull requests.
- **live** — the whole suite. Skipped for fork pull requests, where it could only fail on the
  missing token.

Both publish results into the checks UI via `action-junit-report`, so a failure is readable
without downloading an artifact. The workflow has a job timeout, a `concurrency` group so two
pushes cannot run the suite against the same project simultaneously, and a minimal
`permissions` block.

NOTE: Set `GITLAB_TOKEN` (required) and `GITLAB_PROJECT_ID` (optional) as repository secrets in github repo settings.



The report is a JavaScript app and **cannot be opened over `file://`** — use `allure serve`, or
`python -m http.server` inside the generated directory.

`mvn allure:report` downloads the Allure CLI distribution on first use. Behind a proxy that does
not mirror `io.qameta.allure:allure-commandline:zip` it fails with `Cannot resolve allure
commandline dependencies`; install the CLI directly instead (`brew install allure`, or
`npm i -g allure-commandline`) and use `allure serve target/allure-results`.

Two caveats about the XML, both real:

- **Run `mvn clean test`, not plain `mvn test`, for a full run** — see [Running](#running) above.
  Surefire never deletes a stale report, so after `-Dtest=OneClass` the directory holds a mixture
  and any total computed from it spans two runs.
- **Per-class counts are unreliable while classes run in parallel.** With
  `parallel.mode.classes.default=concurrent`, Surefire's XML writer attributes results to whichever
  test set is open, so a class can be credited with another's tests. The *totals* stay correct, and
  Allure is unaffected because it records each test individually with its real class. If you need
  trustworthy per-class XML, run with `-Djunit.jupiter.execution.parallel.enabled=false`.

### Credential masking — three separate leak paths, three separate fixes

The token is exposed to three independent logging mechanisms, and each needed its own mask; fixing
one does nothing for the others.

**REST Assured's own console logging.** `GitLabClient` turns on
`enableLoggingOfRequestAndResponseIfValidationFails()` so a failing `.then().statusCode(...)` dumps
the request and response instead of just `Expected status code <201> but was <500>`. This fires on
*any* unexpected status — a genuine 500 from GitLab, not only a test that deliberately sends a bad
token — so it is not confined to the authorization tests. Unmasked, it prints `PRIVATE-TOKEN` to
stdout on every such failure, which then flows into a terminal scrollback, a pasted failure report,
or Surefire's own `<system-out>` capture in the JUnit XML that CI uploads as an artifact on every
run — a more mechanical leak path than Allure's, because it needs no report to be published, only
a test to fail. Masked in `GitLabClient.restAssuredConfig()` via
`LogConfig.logConfig().blacklistHeader(TOKEN_HEADER)`.

**`allure-rest-assured` is not used**, for the same reason `blacklistHeader` above does not reach
it: that filter copies request headers straight into the attachment with no redaction hook, so
using it would write `PRIVATE-TOKEN` into `allure-results` and from there into a published report.
`AllureAttachmentFilter` builds the same attachment types against the same
`http-request.ftl` / `http-response.ftl` templates, with credentials masked separately.

**The `verbose` profile's `RequestLoggingFilter`/`ResponseLoggingFilter`** (see
[Verbose logging](#verbose-logging) above) are a third, independent path, discovered the hard way:
their no-arg constructors default `blacklistedHeaders` to an empty set and never consult
`LogConfig`'s blacklist — that blacklist only reaches
`enableLoggingOfRequestAndResponseIfValidationFails()` above, not these filters. A first version of
this feature printed the raw token to console on every verbose run. Fixed in `GitLabClient.base()`
by constructing `RequestLoggingFilter` with the blacklist passed explicitly.
`ResponseLoggingFilter` has no such constructor overload at all, but needs none here — confirmed
against a live response that GitLab never echoes `PRIVATE-TOKEN` back.

Nothing tests any of the three maskings, and nothing can catch one failing: a leaked token does not
fail a run. After touching any of them, run one test that deliberately fails a status check, and
one with `-Pverbose`, and check all three paths by hand:

```bash
mvn test -Dtest=IssueAuthorizationTest 2>&1 | grep -i 'glpat-' && echo LEAK || echo clean       # console (failure-only)
grep -rl 'glpat-' target/allure-results && echo LEAK || echo clean                              # Allure
mvn test -Pverbose -Dtest=IssueReadTest 2>&1 | grep -i 'glpat-' && echo LEAK || echo clean       # console (verbose)
```

The filter lives in `src/test`, since `GitLabClient` cannot import a class from there. It is
chained onto the `RequestSpecification` `GitLabClient` returns at each entry point that needs it —
`ApiTest.issues`, `ApiTest.users`, `anonymousIssues()`, `issuesWithToken(...)` — rather than wired in
centrally. That is four call sites to touch if the filter ever changes, in exchange for
`GitLabClient` having no reporting concern to explain at all. `ProjectsApi`'s calls (scratch-project setup) go through
`GitLabClient.spec()` directly and do not carry the filter, so that request/response pair does not
appear in the report — a small, accepted gap, since it is setup plumbing rather than something the
suite is usually debugging.

**`aspectjweaver` is not wired into surefire's `argLine`.** It is only needed for the `@Step` and
`@Attachment` annotations, which this suite does not use — attachments go through the programmatic
API, which needs no weaving. Report names come from an explicit `@DisplayName` on every test class
and every test method (see below). Add
`@Feature`/`@Severity`/`@Story` — and the weaver — only if someone will actually maintain them;
three Allure dependencies and zero annotations is strictly worse than plain JUnit XML.

### Report names

Every test class and every test method carries an explicit `@DisplayName`, written in the
imperative — "Create an issue with only the mandatory title", not "creates issue with title only".
That is where all report names come from.

