# Findings

Behaviours of the GitLab Issues API that the suite found and that a team owning this endpoint would
want to see. Not all are necessarily bugs — some may be deliberate and undocumented — but each one
changed a test, so each is a question worth asking.

Tests referencing a finding carry the tag `FINDING-n` in a comment, so `grep -rn FINDING- src`
locates every one.

---

## FINDING-1 — `due_date` parsing is lenient, and an unparseable value is silently discarded

**Severity:** medium · **Status:** open, behaviour pinned by two tests · **Revised after a second run**

`POST /projects/:id/issues` never rejects a `due_date`. It always returns **201 Created** — but what
happens to the value splits into two distinct behaviours, which the first version of this finding
wrongly treated as one.

**Non-ISO but unambiguous values are parsed and stored:**

| Value | Form | Stored as |
|---|---|---|
| `31-12-2026` | day-month-year | `2026-12-31` |
| `2026/12/31` | slash-separated | `2026-12-31` |
| `20261231` | ISO basic, no separators | `2026-12-31` |

**Values with no valid reading are accepted and dropped:**

| Value | Why it cannot parse | Stored as |
|---|---|---|
| `not-a-date` | not a date in any format | *(null)* |
| `2026-13-01` | month 13 | *(null)* |
| `2026-02-30` | 30 February | *(null)* |

**How this was corrected.** The original finding claimed all six were discarded, and the test asserted
`dueDate()` was null for every one. On a later run three of the six failed with `2026-12-31` — the
three in the first table. So the endpoint has a lenient date parser, and only genuinely invalid
values are dropped. The original reading was an artefact of asserting one rule over inputs that
behave differently.

**Why it matters.** Two separate problems, and the leniency is the more interesting one:

- *Silent discard* — a client that typos its date format gets a success response and an issue with no
  due date. Nothing surfaces the problem: not the status, not the body. Compare `state_event`, where
  an unknown value *is* rejected with 400; the inconsistency is the part that surprises.
- *Silent reinterpretation* — accepting `31-12-2026` means a non-ISO form is parsed rather than
  refused. That is undocumented, and it means the endpoint is guessing at a format.

**Open question, not yet answered by any test.** `31-12-2026` only proves a day-first form is
accepted; 31 cannot be a month, so no parser could read it any other way. It does **not** establish
how GitLab reads a value where day and month are both plausible — `01-02-2026` is 1 February under
day-first and 2 January under month-first. If it is month-first, then a European client sending
`01-02-2026` silently gets a date ten months wrong with a 201. Determining this needs one test
sending `01-02-2026` and recording which date comes back; it was deliberately not written as a
guess, because the value of this suite is that it does not assert unobserved behaviour.

**Expected:** `400` with the offending field named, as happens for a missing `title` — for both
tables. Failing that, the accepted formats documented, and the ambiguous case defined.

**Tests:**
- `IssueBoundaryTest.parsesNonIsoDueDate` — asserts 201 and `2026-12-31` for the three parsed forms.
- `IssueBoundaryTest.acceptsAndDiscardsUnparseableDueDate` — asserts 201 and a null due date for the
  three unparseable ones. Both pin actual behaviour, so a change in either direction shows up.

---

## FINDING-2 — a repeated label makes issue creation return 500

**Severity:** high · **Status:** open, both tests disabled · **Confirmed — reproduced in a second wire form**

`POST /projects/:id/issues` with the same label twice in the comma-separated `labels` value:

```
labels=qa-label-1a2b3c4d,qa-label-1a2b3c4d
```

returned:

```
500 Internal Server Error
{"message":"500 Internal Server Error"}
```

**Why it matters.** A duplicate in a list is ordinary client input — a label added twice by a form,
a merge of two label sets, a retried edit. It should be de-duplicated or rejected with a 4xx.
A 500 means an unhandled exception on the server, so it is a defect regardless of what the intended
handling is.

**Expected:** either `201` with the label stored once, or `400`. Never `5xx`.

**Confirmed, and the earlier caveat is resolved.** This was originally seen once, so a transient
gitlab.com fault could not be ruled out. It has since been reproduced in a *second, independent wire
form*, which both rules out a one-off and answers the question below. The finding no longer rests on
a single observation.

**The CSV hypothesis is disproven.** The original failing request used the documented comma-separated
form, `labels=x,x`, so the open question was whether the defect was really in GitLab's comma-splitting
rather than in duplicate handling. `aRepeatedLabelInAJsonArrayDoesNotFail` was written as the
discriminator: send the same duplicate as a JSON array, `labels=["x","x"]`. It returned **500 as
well**. So the encoding is irrelevant and the defect is in duplicate handling generally — a broader
bug than the localised parsing one hypothesised, and it removes the "the serializer walks into it"
theory entirely.

Note that `acceptsLabelsAsAJsonArrayToo` **passes**: the array form is accepted for a single label
and returns 201. So the array wire form works; only the duplicate breaks it. That isolates the
trigger to duplication alone.

**Expected:** either `201` with the label stored once, or `400`. Never `5xx`, in either wire form.

**Tests:** both `@Disabled` referencing this finding, both asserting the behaviour the endpoint
*should* have, so re-enabling them is the test for the fix. Deliberately *not* weakened to
`statusCode() < 500`, which would go green the day the bug was fixed and stop reporting it meanwhile.
- `IssueBoundaryTest.deduplicatesRepeatedLabels` — the comma-separated form.
- `IssueBoundaryTest.aRepeatedLabelInAJsonArrayDoesNotFail` — the JSON-array form. Ran as the
  discriminator described above; disabled once it had answered.
- `IssueBoundaryTest.aRepeatedLabelDoesNotCorruptTheProject` — **still runs**, and checks the thing
  that would be worse than the 500 itself: that listing still works afterwards. A 500 that left the
  collection unreadable would be a far more serious defect than a rejected create.

---

## FINDING-3 — a non-positive `page` is coerced to the first page, not rejected

**Severity:** low · **Status:** open, behaviour pinned by a test

`GET /projects/:id/issues?page=0` and `page=-1` return **200** with `X-Page: 1` — the unusable value
is silently coerced to the first page rather than rejected.

**Why it matters.** Mild, and arguably the friendlier choice. It is recorded because it is
*inconsistent* with how the same endpoint treats other invalid parameters: an unsupported `state`,
`sort`, `order_by`, `scope` or `issue_type` value is rejected with 400 (see `IssueValidationTest`).
A client that computed a page number wrongly — an off-by-one landing on 0 — gets page 1 and a
success status, so a paging bug reads as working code.

**Expected:** either `400`, matching the other invalid-parameter cases, or the current coercion
documented as deliberate.

**Tests:**
- `IssuePaginationTest.coercesNonPositivePageNumbers` — asserts the actual behaviour (200,
  `X-Page: 1`, first page returned) for `0` and `-1`.
- `IssuePaginationTest.handlesNonNumericPageNumber` — kept separate, because a value that is not an
  integer at all may legitimately be rejected where `0` and `-1` are coerced. Accepts either
  outcome and only rules out a 5xx or paging somewhere unexpected.

This replaced a single test that asserted the opposite — that an invalid page must *not* become
page 1. That assertion failed on two of its three values, which is what surfaced the coercion.

---

## FINDING-4 — an issue's `web_url` now points at `/-/work_items/:iid`

**Severity:** low · **Status:** open, behaviour pinned by a test

`web_url` on an issue came back as:

```
https://gitlab.com/<namespace>/<project>/-/work_items/903
```

where this suite previously observed `/-/issues/903`. This is GitLab's Work Items migration
surfacing in the issues API response.

**Why it matters.** It is a live change to a response field, which is exactly the class of drift a
contract suite exists to catch. Any consumer that pattern-matches `web_url` for `/issues/` — to
build a link, scrape an id, or route a webhook — breaks silently, and neither the JSON schema
(`web_url` is only constrained to `^https?://`) nor the typed model would notice, because the field
is still a present, non-empty string.

**Expected:** no assertion either way on which segment GitLab uses; the suite should not fail when a
URL shape changes to an equivalent one. Pinned at the level that is actually a contract — an
absolute URL addressing the issue by its `iid`.

**Test:** `IssueReadTest.returnsEveryFieldThatWasSetOnCreation` — asserts `web_url` is absolute and
ends with the issue's `iid`, and accepts either `/issues/` or `/work_items/` as the path segment.

---


