# Coverage

What this suite tests, what it deliberately does not, and the API behaviours worth flagging.

## Endpoints

Positive and negative cases for `GET .../:iid`, `PUT` and `DELETE` each live in a separate class -
`IssueXTest` for the happy path, `IssueXNegativeTest` (or, for reads, `IssueReadNotFoundTest`) for
the error path - so either can be read on its own.

| Endpoint | Class | Notes |
|---|---|---|
| `POST /projects/:id/issues` | `IssueCreateTest` | mandatory-only, every field a plain token can set, defaults, every `issue_type`, assignment, iid monotonicity, read-your-writes |
| `GET /projects/:id/issues/:iid` | `IssueReadTest` | full-field read-back, every set field returned |
| `GET /projects/:id/issues/:iid` | `IssueReadNotFoundTest` | `iid` vs `id`, unknown / zero / negative iid, unknown project |
| `GET /projects/:id/issues` | `IssueListFilterTest` | the filter matrix below |
| `GET /projects/:id/issues` | `IssuePaginationTest` | headers, boundaries, keyset |
| `PUT /projects/:id/issues/:iid` | `IssueUpdateTest` | field updates, label add/remove/replace, state transitions, persistence, timestamps |
| `PUT /projects/:id/issues/:iid` | `IssueUpdateNegativeTest` | unknown internal id, clearing the mandatory title |
| `DELETE /projects/:id/issues/:iid` | `IssueDeleteTest` | removal, listing effect, closed/confidential, iid non-reuse |
| `DELETE /projects/:id/issues/:iid` | `IssueDeleteNegativeTest` | repeat delete, unknown id/project, listing unchanged by a failed delete |

## Filters

| Parameter | Covered | Test |
|---|---|---|
| `state` (`opened`/`closed`/`all`) | ✅ | `filtersByState`, `returnsBothStatesWhenFilteringByAll` |
| `labels` (single, multiple/AND) | ✅ | `filtersByLabel`, `requiresAllLabelsToMatch` |
| `labels=None` / `Any` | ✅ | `filtersByAbsenceOfLabels`, `filtersByPresenceOfAnyLabel` |
| `not[labels]` | ✅ | `excludesLabelWithNegationFilter` |
| `search` | ✅ | `searchesTitles`, `returnsEmptyListWhenNothingMatches` |
| `in=title` / `in=description` | ✅ | `restrictsSearchToTheRequestedField` |
| `author_id` | ✅ | `filtersByAuthor` |
| `assignee_id` (id and `None`) | ✅ | `filtersByAssignee`, `filtersByAbsenceOfAssignee` |
| `confidential` | ✅ | `filtersByConfidentiality` |
| `issue_type` | ✅ | `filtersByIssueType` |
| `iids[]` (multiple, partial match) | ✅ | `filtersByExplicitInternalIds`, `ignoresUnknownInternalIdsInTheIidsFilter` |
| `created_after` / `created_before` | ✅ | `filtersByCreationWindow`, `excludesIssuesCreatedAfterTheUpperBound` |
| `due_date=0` | ✅ | `filtersByAbsenceOfDueDate` |
| `scope` | ✅ | `returnsOnlyIssuesCreatedByTheCallerForCreatedByMeScope` |
| `order_by` + `sort` | ✅ | `sortsByCreationDateAscending`, `sortsByCreationDateDescendingByDefault`, `sortsByTitle` |
| combined / contradictory filters | ✅ | `combinesFiltersConjunctively`, `returnsEmptyListForContradictoryFilters` |
| invalid filter values | ✅ | `IssueValidationTest` — state, sort, order_by, scope, issue_type |
| `assignee_username`, `author_username`, `milestone`, `non_archived`, `with_labels_details`, `updated_after/before` | expressible in `IssueQuery` but not asserted | live coverage would need extra project fixtures — see out of scope |

## Pagination

| Behaviour | Test |
|---|---|
| `X-Total`, `X-Total-Pages`, `X-Page`, `X-Per-Page` | `reportsTotalsForAScopedCollection` |
| `X-Prev-Page` absent on first page | `omitsPreviousPageOnTheFirstPage` |
| `X-Next-Page` absent on last page | `omitsNextPageOnTheLastPage` |
| `Link` rel=first/prev/next/last | `offersNavigableLinkHeaders` |
| default page size is 20 | `appliesTheDocumentedDefaultPageSize` |
| `per_page=101` clamped to 100 | `clampsPageSizeAboveTheDocumentedMaximum` |
| `per_page` 0 / negative | `handlesNonPositivePageSizes` |
| `page` 0 / negative coerced to first page | `coercesNonPositivePageNumbers` (FINDING-3) |
| `page` non-numeric | `handlesNonNumericPageNumber` |
| beyond last page → empty array | `returnsAnEmptyPageBeyondTheLastOne` |
| keyset pagination | `supportsKeysetPagination` |
| **not covered:** walking every page yields each item exactly once | the multi-page walk test was removed as too slow for its value, and `IssuesApi.listAllPages` with it — re-adding this means restoring both |

## Boundaries and unusual inputs — `IssueBoundaryTest`

- **Title**: 255 accepted, 256 rejected, 1 character, blank/whitespace/tab/newline rejected,
  surrounding whitespace trimmed
- **Round-trip fidelity**: unicode (CJK, Greek, Arabic RTL), emoji (surrogate pairs), HTML/markdown/
  `<script>`, SQL-shaped input — all asserted verbatim through a fresh `GET`, not just the create
  response, so a transformation applied on read is also caught
- **Description**: 50 000 characters, empty, cleared on update, multiline/tabs preserved exactly
- **Labels**: empty elements ignored, JSON-array wire form accepted, spaces allowed, 25 labels,
  cleared with an empty set, removing an absent label is a no-op. Duplicate handling is **not**
  covered: both wire forms return 500 (FINDING-2), so `deduplicatesRepeatedLabels` and
  `aRepeatedLabelInAJsonArrayDoesNotFail` are `@Disabled` asserting the correct behaviour, and
  `aRepeatedLabelDoesNotCorruptTheProject` runs to check the project stays usable afterwards
- **Dates**: past accepted, year 2999 accepted, cleared on update. Malformed values split two ways
  (FINDING-1): `31-12-2026`, `2026/12/31` and `20261231` are **parsed** as 2026-12-31, while
  `not-a-date`, `2026-13-01` and `2026-02-30` are accepted with a 201 and **silently discarded** —
  neither is rejected
- **Assignees**: unknown user, `0` as the unassign sentinel
- **State**: closing twice is idempotent, unknown `state_event` rejected

## Authentication and authorization — `IssueAuthorizationTest`

Anonymous list/read/create/delete, five malformed token shapes, error-body schema, and two
information-disclosure checks: an inaccessible project must answer `404` rather than `403`, and an
`iid` must not resolve across projects. The rejected-delete test also verifies the issue still
exists afterwards — a status-code-only assertion would miss a delete that happened anyway.

## Response contract — `IssueSchemaTest` / `IssueErrorSchemaTest`

Every response shape validated against `schemas/*.json`. `IssueSchemaTest` covers the success
shapes: single issue (minimal and fully populated), collection, single-element collection, empty
collection. `IssueErrorSchemaTest` covers the 404 and 400 error bodies. The schemas assert types and
required fields but do **not** forbid additional properties — the goal is to catch the contract
narrowing, not to break the build whenever GitLab ships a field.

## Deliberately out of scope

| Area | Why |
|---|---|
| `weight`, `epic_id`, iterations, `health_status` | Premium/Ultimate. Accepted and silently ignored on Free tier, so a test would assert nothing. |
| `milestone_id` and `milestone` filtering | Needs a milestone fixture, which means owning the lifecycle of a different resource. This suite is issue CRUD; managing project sub-resources is out of scope. |
| `created_at` and `iid` on create | Need administrator or project-owner rights. Both are accepted and silently ignored otherwise, which is recorded below rather than tested. |
| `merge_request_to_resolve_discussions_of`, `discussion_to_resolve` | Need a merge request with an open discussion — a fixture chain well beyond the issues endpoint. |
| `my_reaction_emoji` | Needs award-emoji setup through a different endpoint. |
| Multi-user permission matrix (Guest/Reporter/Developer/Maintainer) | Needs several tokens for users at different membership levels. The single-token cases — anonymous, invalid, and owner — are covered; the Maintainer-gets-403-on-delete rule is documented rather than tested. |
| Rate-limit behaviour under load | Deliberately triggering `429` against gitlab.com is antisocial and slow. `RetryFilter` handles it; a stub-server test of the filter itself would be the right way to cover the retry logic. |
| Webhooks / notifications side effects | Out of scope for the issues endpoint. |

## API behaviours worth flagging

These are not framework bugs — they are things a team owning this API would want to know, and
several are pinned by tests specifically so a change to them shows up deliberately.

1. **Three create fields are accepted and silently ignored when their precondition is not met.**
   `created_at` and `iid` need administrator or project-owner rights; `weight` needs
   Premium/Ultimate. In every case the request returns 201 and the value is dropped, with nothing in
   the response saying so — a caller gets a success and an issue that does not match what it asked
   for. Recorded rather than tested: asserting the honoured path needs elevated rights or a paid
   tier, and asserting the ignored path pins an accident of the current token's permissions.
2. **An unassignable user is silently dropped rather than rejected.** `ignoresOrRejectsUnknownAssignee`
   accepts either outcome but asserts the issue never claims an assignment that did not happen.
3. **`issue_type: test_case` is accepted and downgraded to `issue`** on the Free tier, with no
   error. Omitted from `IssueType` for that reason.
4. **Empty and absent are not the same input** for `description`, but the API normalises empty to
   `null` — so a round-trip cannot distinguish them.
