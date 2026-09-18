package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.UpdateIssueRequest;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundaries, unusual inputs and round-trip fidelity.
 * <p>
 * The recurring theme is that the API is a data store, not a renderer: whatever goes in must come
 * back byte-identical. Markup, quotes and emoji are therefore not "injection tests" expecting a
 * rejection - they assert the opposite, that nothing is helpfully escaped, stripped or normalised on
 * the way through. Silent mutation of stored text is a far more common and far more damaging bug
 * than a rejected payload.
 */
@DisplayName("Issue field boundaries and unusual inputs")
class IssueBoundaryTest extends ApiTest {

    // ---------- title ----------

    @Test
    @DisplayName("Accept a title at the documented 255-character maximum")
    void acceptsTitleAtMaximumLength() {
        String title = TestData.titleOfLength(Issue.TITLE_MAX_LENGTH);

        Issue created = givenIssue(CreateIssueRequest.builder().title(title).build());

        assertThat(created.title()).hasSize(Issue.TITLE_MAX_LENGTH).isEqualTo(title);
    }

    @Test
    @DisplayName("Reject a title one character beyond the maximum")
    void rejectsTitleBeyondMaximumLength() {
        String title = TestData.titleOfLength(Issue.TITLE_MAX_LENGTH + 1);

        Response response = createRawTracked(Map.of("title", title));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Accept a single-character title")
    void acceptsSingleCharacterTitle() {
        Issue created = givenIssue(CreateIssueRequest.builder().title("x").build());

        assertThat(created.title()).isEqualTo("x");
    }

    @ParameterizedTest(name = "Blank title is rejected: [{0}]")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Reject a blank title")
    void rejectsBlankTitle(String title) {
        Response response = createRawTracked(Map.of("title", title));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Trim surrounding whitespace from the title")
    void trimsSurroundingWhitespaceFromTitle() {
        String core = TestData.uniqueTitle();

        Issue created = givenIssue(CreateIssueRequest.builder().title("  " + core + "  ").build());

        // GitLab strips leading and trailing whitespace. Worth pinning either way, because a suite
        // that only ever sends clean titles cannot tell trimming from storage-as-is.
        assertThat(created.title()).isEqualTo(core);
    }

    static Stream<Arguments> unusualTitles() {
        return Stream.of(
                Arguments.of("unicode", TestData.unicodeTitle()),
                Arguments.of("emoji", TestData.emojiTitle()),
                Arguments.of("markup", TestData.markupTitle()),
                Arguments.of("sql-shaped", TestData.sqlishTitle()));
    }

    @ParameterizedTest(name = "{0} title round-trips verbatim")
    @MethodSource("unusualTitles")
    @DisplayName("Store unusual titles verbatim")
    void storesUnusualTitlesVerbatim(String kind, String title) {
        Issue created = givenIssue(CreateIssueRequest.builder().title(title).build());

        Issue fetched = issues.get(projectId(), created.iid());

        // Verified through a fresh GET, not just the create response, so a transformation applied on
        // read rather than on write is also caught.
        assertThat(created.title()).as("%s title on create", kind).isEqualTo(title);
        assertThat(fetched.title()).as("%s title on read", kind).isEqualTo(title);
    }

    // ---------- description ----------

    @Test
    @DisplayName("Accept a large description well inside the ~1 MB limit")
    void acceptsLargeDescription() {
        // Well inside GitLab's ~1 MB limit but far beyond anything a hand-written test would use.
        String description = "x".repeat(50_000);

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description(description)
                .build());

        assertThat(created.description()).hasSize(50_000);
    }

    @Test
    @DisplayName("Accept an empty description")
    void acceptsEmptyDescription() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("")
                .build());

        // Empty and absent are different inputs; the API normalises empty to null.
        assertThat(created.description()).isIn(null, "");
    }

    @Test
    @DisplayName("Clear the description on update")
    void clearsDescriptionOnUpdate() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("to be cleared")
                .build());

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .description("")
                .build());

        assertThat(updated.description()).isIn(null, "");
    }

    @Test
    @DisplayName("Preserve a multiline description exactly, including tabs")
    void preservesMultilineDescriptionExactly() {
        String description = "line one\nline two\n\n  indented\ttabbed";

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description(description)
                .build());

        assertThat(issues.get(projectId(), created.iid()).description()).isEqualTo(description);
    }

    // ---------- labels ----------

    @Test
    @Disabled("FINDING-2: a repeated label makes POST /issues return 500 - see FINDINGS.md")
    @DisplayName("Deduplicate a repeated label (disabled - FINDING-2)")
    void deduplicatesRepeatedLabels() {
        String marker = TestData.uniqueLabel();

        // Sending labels="x,x" answered 500 Internal Server Error, so this asserts the behaviour
        // the endpoint should have rather than the one it has. Disabled rather than deleted or
        // weakened to statusCode() < 500: a permanently red test trains people to ignore the
        // report, and a test that tolerates the 500 would stop reporting the bug the day it is
        // fixed. The finding is tracked in FINDINGS.md; re-enable when it is resolved.
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(marker + "," + marker)
                .build());

        assertThat(created.labels()).containsExactly(marker);
    }

    @Test
    @DisplayName("Leave the project usable after a repeated label returns 500")
    void aRepeatedLabelDoesNotCorruptTheProject() {
        String marker = TestData.uniqueLabel();

        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "labels", marker + "," + marker));

        // Whatever the endpoint decides about duplicates, the project must remain usable
        // afterwards - a 500 that left the collection unreadable would be far more serious than
        // the 500 itself. Listing is the check that matters here.
        assertThat(issues.listRaw(projectId(), com.qa.gitlab.model.IssueQuery.builder().perPage(1).build())
                .statusCode())
                .as("listing must still work after a duplicate-label create returned %d", response.statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Ignore empty elements in the comma-separated labels string")
    void ignoresEmptyLabelElements() {
        String marker = TestData.uniqueLabel();

        // Serialises to "marker,," - the empty segments must not become empty labels.
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(marker + ",,")
                .build());

        assertThat(created.labels()).containsExactly(marker);
    }

    @Test
    @DisplayName("Accept labels as a JSON array in addition to the documented comma-separated string")
    void acceptsLabelsAsAJsonArrayToo() {
        String marker = TestData.uniqueLabel();

        // Sent as a raw Map, so Jackson emits a JSON array rather than the comma-separated string
        // CreateIssueRequest.labels() sends. The documented shape for `labels` is the comma-separated
        // string; this establishes whether the array form is accepted too.
        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "labels", List.of(marker)));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.as(Issue.class).labels()).containsExactly(marker);
    }

    @Test
    @Disabled("FINDING-2: a repeated label returns 500 in the JSON-array form too - see FINDINGS.md")
    @DisplayName("Deduplicate a repeated label sent as a JSON array (disabled - FINDING-2)")
    void aRepeatedLabelInAJsonArrayDoesNotFail() {
        String marker = TestData.uniqueLabel();

        // FINDING-2, second wire form. This ran as the discriminator for the CSV hypothesis: the
        // original 500 came from the comma-separated form "x,x", so if the JSON-array form had
        // survived the same duplicate the defect would have been localised to GitLab's
        // comma-splitting. It returned 500 as well. That disproves the hypothesis and confirms the
        // defect is in duplicate handling generally, independent of how the labels were encoded -
        // and it is the second independent observation, so the finding no longer rests on one run.
        //
        // Now @Disabled on the same reasoning as deduplicatesRepeatedLabels above: it asserts the
        // behaviour the endpoint should have, so re-enabling it is the test for the fix. Deliberately
        // not left asserting statusCode() < 500, which would go green the day the bug was fixed and
        // stop reporting it in the meantime.
        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "labels", List.of(marker, marker)));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.as(Issue.class).labels()).containsExactly(marker);
    }

    @Test
    @DisplayName("Accept a label containing spaces")
    void acceptsLabelWithSpaces() {
        String marker = TestData.uniqueLabel() + " with spaces";

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(marker)
                .build());

        assertThat(created.labels()).containsExactly(marker);
    }

    @Test
    @DisplayName("Accept 25 labels on a single issue")
    void acceptsManyLabels() {
        List<String> many = Stream.generate(TestData::uniqueLabel).limit(25).toList();

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(String.join(",", many))
                .build());

        assertThat(created.labels()).containsExactlyInAnyOrderElementsOf(many);
    }

    @Test
    @DisplayName("Clear every label with an empty label set")
    void clearsAllLabelsWithAnEmptyLabelSet() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(TestData.uniqueLabel())
                .build());

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .labels("")
                .build());

        assertThat(updated.labels()).isEmpty();
    }

    @Test
    @DisplayName("Treat removing an absent label as a no-op")
    void removingAnAbsentLabelIsANoOp() {
        String kept = TestData.uniqueLabel();
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(kept)
                .build());

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .removeLabels(TestData.uniqueLabel())
                .build());

        assertThat(updated.labels()).containsExactly(kept);
    }

    // ---------- dates ----------

    @Test
    @DisplayName("Accept a due date in the past")
    void acceptsDueDateInThePast() {
        LocalDate past = LocalDate.now().minusDays(30);

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .dueDate(past)
                .build());

        // Accepted by design - a due date can legitimately be overdue. Pinned so that a future
        // tightening of validation shows up as a deliberate decision rather than a surprise.
        assertThat(created.dueDate()).isEqualTo(past);
    }

    @Test
    @DisplayName("Accept a due date far in the future")
    void acceptsDueDateFarInTheFuture() {
        LocalDate far = LocalDate.of(2999, 12, 31);

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .dueDate(far)
                .build());

        assertThat(created.dueDate()).isEqualTo(far);
    }

    @ParameterizedTest(name = "Non-ISO due_date {0} is parsed, not discarded")
    @ValueSource(strings = {"31-12-2026", "2026/12/31", "20261231"})
    @DisplayName("Parse a non-ISO due_date rather than discarding it (FINDING-1)")
    void parsesNonIsoDueDate(String dueDate) {
        Response response = createRawTracked(
                Map.of("title", TestData.uniqueTitle(), "due_date", dueDate));

        // FINDING-1, first half. None of these three is the documented ISO form, but each is an
        // unambiguous way to write 31 December 2026 - day-month-year, slash-separated, and ISO
        // basic - and GitLab parses and stores all three. So the endpoint is lenient about the
        // format rather than strict, which is only observable by sending something non-ISO.
        //
        // This half was originally lumped in with the unparseable values below and asserted to come
        // back null; three of the six then failed with 2026-12-31, which is what separated leniency
        // from silent discard. See FINDINGS.md for the open question this leaves: 31-12-2026 proves
        // a day-first form is accepted, but nothing here establishes how GitLab reads a value where
        // day and month are both plausible, such as 01-02-2026.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.as(Issue.class).dueDate())
                .as("a non-ISO but unambiguous date must be parsed, not dropped")
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @ParameterizedTest(name = "Unparseable due_date is silently discarded: {0}")
    @ValueSource(strings = {"not-a-date", "2026-13-01", "2026-02-30"})
    @DisplayName("Accept and silently discard an unparseable due_date (FINDING-1)")
    void acceptsAndDiscardsUnparseableDueDate(String dueDate) {
        Response response = createRawTracked(
                Map.of("title", TestData.uniqueTitle(), "due_date", dueDate));

        // FINDING-1, second half. These have no valid reading at all - not a date, month 13,
        // 30 February - and each returns 201 with the field dropped, nothing in the response saying
        // it was ignored. A client that typoed its date format gets a successful create and an issue
        // with no due date. Pinned as-is rather than left failing, because the silent discard is the
        // thing worth noticing if it ever changes. Compare state_event, where an unknown value *is*
        // rejected with 400; that inconsistency is the part that surprises.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.as(Issue.class).dueDate())
                .as("a value with no valid date reading must not be stored as a real date")
                .isNull();
    }

    @ParameterizedTest(name = "Unparseable start_date is silently discarded: {0}")
    @ValueSource(strings = {"not-a-date", "2026-13-01", "2026-02-30"})
    @DisplayName("Accept and silently discard an unparseable start_date (FINDING-1)")
    void acceptsAndDiscardsUnparseableStartDate(String startDate) {
        Response response = createRawTracked(
                Map.of("title", TestData.uniqueTitle(), "start_date", startDate));

        // FINDING-1 applies to start_date exactly as it does to due_date: accepted with a 201 and
        // dropped, nothing in the response saying so. Pinned separately because the two fields are
        // validated independently, and because it is the contrast that makes the finding sharp -
        // created_at with the same class of garbage is rejected with a 400 (see
        // IssueValidationTest.rejectsMalformedCreatedAt). Three date fields, two different rules.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.as(Issue.class).startDate())
                .as("a value with no valid date reading must not be stored as a real start date")
                .isNull();
    }

    @Test
    @DisplayName("Accept a start date equal to the due date")
    void acceptsStartDateEqualToDueDate() {
        LocalDate same = LocalDate.now().plusDays(7);

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .startDate(same)
                .dueDate(same)
                .build());

        // A one-day task is legitimate, so the boundary of the ordering rule must be inclusive.
        assertThat(created.startDate()).isEqualTo(same);
        assertThat(created.dueDate()).isEqualTo(same);
    }

    @Test
    @DisplayName("Accept a start date in the past")
    void acceptsStartDateInThePast() {
        LocalDate past = LocalDate.now().minusDays(30);

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .startDate(past)
                .build());

        assertThat(created.startDate()).isEqualTo(past);
    }

    @Test
    @DisplayName("Reject a start date after the due date on create")
    void rejectsStartDateAfterDueDateOnCreate() {
        LocalDate due = LocalDate.now().plusDays(3);
        LocalDate startAfterDue = due.plusDays(1);

        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "start_date", startAfterDue.toString(),
                "due_date", due.toString()));

        // An issue that starts after it is due is incoherent, so the two fields have to be validated
        // against each other rather than only individually. If this returns 201, GitLab does not
        // enforce the ordering and that is a finding to record, not a test to weaken - the stored
        // pair would then be a contradiction any consumer has to defend against.
        assertThat(response.statusCode())
                .as("start_date after due_date must be rejected; a 201 here means the ordering is "
                        + "not validated and the pair was stored as a contradiction")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("Reject a due date moved before the start date on update")
    void rejectsDueDateBeforeStartDateOnUpdate() {
        LocalDate start = LocalDate.now().plusDays(10);
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .startDate(start)
                .dueDate(start.plusDays(5))
                .build());

        // The same rule has to hold on update, where only one half of the pair is being changed and
        // the other comes from stored state - the case a create-only check would miss entirely.
        Response response = issues.updateRaw(projectId(), created.iid(),
                Map.of("due_date", start.minusDays(1).toString()));

        assertThat(response.statusCode())
                .as("moving due_date before the stored start_date must be rejected")
                .isEqualTo(400);
        assertThat(issues.get(projectId(), created.iid()).dueDate())
                .as("a rejected update must not have changed the stored due date")
                .isEqualTo(start.plusDays(5));
    }

    @Test
    @DisplayName("Clear the due date on update")
    void clearsDueDateOnUpdate() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .dueDate(LocalDate.now().plusDays(5))
                .build());

        Response response = issues.updateRaw(projectId(), created.iid(),
                Map.of("due_date", ""));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.as(Issue.class).dueDate()).isNull();
    }

    // ---------- assignees ----------

    @Test
    @DisplayName("Ignore or reject an unknown assignee without claiming the assignment")
    void ignoresOrRejectsUnknownAssignee() {
        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "assignee_ids", List.of(TestData.UNKNOWN_PROJECT_ID)));

        // GitLab silently drops an unassignable user rather than failing. Either outcome is
        // acceptable; creating the issue *and* claiming the assignment would not be.
        if (response.statusCode() == 201) {
            assertThat(response.as(Issue.class).assignees()).isEmpty();
        } else {
            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Clear every assignee using the documented 0 sentinel")
    void clearsAssigneesWithZero() {
        long selfId = selfId();
        Issue assigned = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .assigneeIds(List.of(selfId))
                .build());

        // 0 is GitLab's documented "unassign everyone" sentinel.
        Issue cleared = issues.update(projectId(), assigned.iid(), UpdateIssueRequest.builder()
                .assigneeIds(List.of(0L))
                .build());

        assertThat(cleared.assignees()).isEmpty();
    }

    // ---------- state transitions ----------

    @Test
    @DisplayName("Be idempotent when closing an already-closed issue")
    void closingAnAlreadyClosedIssueIsIdempotent() {
        Issue created = givenIssue();
        Issue closedOnce = issues.update(projectId(), created.iid(),
                UpdateIssueRequest.builder().stateEvent(com.qa.gitlab.model.StateEvent.CLOSE).build());

        Issue closedTwice = issues.update(projectId(), created.iid(),
                UpdateIssueRequest.builder().stateEvent(com.qa.gitlab.model.StateEvent.CLOSE).build());

        assertThat(closedTwice.state()).isEqualTo(closedOnce.state());
        assertThat(closedTwice.closedAt()).isNotNull();
    }

    @Test
    @DisplayName("Reject an unknown state_event value")
    void rejectsUnknownStateEvent() {
        Issue created = givenIssue();

        Response response = issues.updateRaw(projectId(), created.iid(),
                Map.of("state_event", "obliterate"));

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
