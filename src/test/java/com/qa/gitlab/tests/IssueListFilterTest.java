package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.FilterValue;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueOrderBy;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.model.IssueScope;
import com.qa.gitlab.model.IssueState;
import com.qa.gitlab.model.IssueStateFilter;
import com.qa.gitlab.model.IssueType;
import com.qa.gitlab.model.SearchIn;
import com.qa.gitlab.model.SortDirection;
import com.qa.gitlab.model.StateEvent;
import com.qa.gitlab.model.UpdateIssueRequest;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.Schemas;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filters on {@code GET /projects/:id/issues}.
 * <p>
 * Every test here scopes its assertion to data it created itself, using a unique label or a unique
 * search string. That matters more than it looks: the suite runs against a shared project, classes
 * run concurrently, and the project may already contain issues. An assertion phrased against the
 * whole collection - "exactly three results" - would pass on an empty project and fail everywhere
 * else, which is the classic way a filter suite becomes untrustworthy.
 * <p>
 * The complementary risk is the opposite one: a filter that is silently ignored by the server
 * returns the unfiltered collection, which still <em>contains</em> the expected issue. So wherever a
 * filter is meant to exclude something, a negative control is created and asserted absent.
 */
@DisplayName("GET /projects/:id/issues - filters")
class IssueListFilterTest extends ApiTest {

    @Test
    @DisplayName("Filter by a single label")
    void filtersByLabel() {
        String marker = TestData.uniqueLabel();
        Issue tagged = givenIssue(labelled(marker));
        Issue untagged = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(tagged.iid())
                .doesNotContain(untagged.iid());
    }

    @Test
    @DisplayName("Require every requested label to match, not just one")
    void requiresAllLabelsToMatch() {
        String first = TestData.uniqueLabel();
        String second = TestData.uniqueLabel();
        Issue both = givenIssue(labelled(first, second));
        Issue onlyFirst = givenIssue(labelled(first));

        // labels is an AND, not an OR - a suite that only ever passes one label never learns this.
        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(first, second))
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(both.iid())
                .doesNotContain(onlyFirst.iid());
    }

    @Test
    @DisplayName("Filter by absence of any label (labels=None)")
    void filtersByAbsenceOfLabels() {
        Issue unlabelled = givenIssue();
        Issue labelled = givenIssue(labelled(TestData.uniqueLabel()));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labelsPresence(FilterValue.none())
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(unlabelled.iid())
                .doesNotContain(labelled.iid());
    }

    @Test
    @DisplayName("Filter by presence of any label (labels=Any)")
    void filtersByPresenceOfAnyLabel() {
        Issue unlabelled = givenIssue();
        Issue labelled = givenIssue(labelled(TestData.uniqueLabel()));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labelsPresence(FilterValue.any())
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(labelled.iid())
                .doesNotContain(unlabelled.iid());
    }

    @Test
    @DisplayName("Exclude a label through the not[labels] negation filter")
    void excludesLabelWithNegationFilter() {
        String marker = TestData.uniqueLabel();
        Issue tagged = givenIssue(labelled(marker));
        Issue untagged = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .not(Map.of("labels", marker))
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(untagged.iid())
                .doesNotContain(tagged.iid());
    }

    @Test
    @DisplayName("Filter by state")
    void filtersByState() {
        Issue open = givenIssue();
        Issue closed = givenIssue();
        issues.update(projectId(), closed.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        List<Issue> openOnly = issues.list(projectId(), IssueQuery.builder()
                .state(IssueStateFilter.OPENED)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(openOnly)
                .allSatisfy(issue -> assertThat(issue.state()).isEqualTo(IssueState.OPENED))
                .extracting(Issue::iid)
                .contains(open.iid())
                .doesNotContain(closed.iid());
    }

    @Test
    @DisplayName("Return both open and closed issues when filtering by state=all")
    void returnsBothStatesWhenFilteringByAll() {
        Issue open = givenIssue();
        Issue closed = givenIssue();
        issues.update(projectId(), closed.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        List<Issue> all = issues.list(projectId(), IssueQuery.allStatesMaxPage());

        assertThat(all).extracting(Issue::iid).contains(open.iid(), closed.iid());
    }

    @Test
    @DisplayName("Search titles")
    void searchesTitles() {
        String needle = TestData.uniqueTitle("needle");
        Issue match = givenIssue(CreateIssueRequest.builder().title(needle).build());
        Issue other = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .search(needle)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(match.iid())
                .doesNotContain(other.iid());
    }

    @Test
    @DisplayName("Restrict search to the requested field via in=title or in=description")
    void restrictsSearchToTheRequestedField() {
        String needle = TestData.uniqueTitle("desc-only");
        Issue inDescription = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("contains " + needle + " in the body")
                .build());

        List<Issue> inTitle = issues.list(projectId(), IssueQuery.builder()
                .search(needle)
                .in(List.of(SearchIn.TITLE))
                .build());
        List<Issue> inBody = issues.list(projectId(), IssueQuery.builder()
                .search(needle)
                .in(List.of(SearchIn.DESCRIPTION))
                .build());

        assertThat(inTitle).extracting(Issue::iid).doesNotContain(inDescription.iid());
        assertThat(inBody).extracting(Issue::iid).contains(inDescription.iid());
    }

    @Test
    @DisplayName("Return an empty array, not a 404, when nothing matches")
    void returnsEmptyListWhenNothingMatches() {
        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .search("no-issue-will-ever-contain-" + TestData.uniqueLabel())
                .build());

        // An empty result must be an empty array, not a 404 and not a null body.
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Filter by author")
    void filtersByAuthor() {
        Issue mine = givenIssue();
        long selfId = mine.author().id();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .authorId(selfId)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found)
                .isNotEmpty()
                .allSatisfy(issue -> assertThat(issue.author().id()).isEqualTo(selfId))
                .extracting(Issue::iid)
                .contains(mine.iid());
    }

    @Test
    @DisplayName("Filter by assignee")
    void filtersByAssignee() {
        long selfId = selfId();
        Issue assigned = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("assigned"))
                .assigneeIds(List.of(selfId))
                .build());
        Issue unassigned = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .assigneeId(FilterValue.of(selfId))
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(assigned.iid())
                .doesNotContain(unassigned.iid());
    }

    @Test
    @DisplayName("Filter by absence of an assignee (assignee_id=None)")
    void filtersByAbsenceOfAssignee() {
        long selfId = selfId();
        Issue unassigned = givenIssue();
        Issue assigned = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("assigned"))
                .assigneeIds(List.of(selfId))
                .build());

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .assigneeId(FilterValue.none())
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(unassigned.iid())
                .doesNotContain(assigned.iid());
    }

    @Test
    @DisplayName("Filter by confidentiality")
    void filtersByConfidentiality() {
        Issue confidential = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("secret"))
                .confidential(true)
                .build());
        Issue open = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .confidential(true)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(confidential.iid())
                .doesNotContain(open.iid());
    }

    @Test
    @DisplayName("Filter by issue_type")
    void filtersByIssueType() {
        Issue task = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("task"))
                .issueType(IssueType.TASK)
                .build());
        Issue plain = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .issueType(IssueType.TASK)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(task.iid())
                .doesNotContain(plain.iid());
    }

    @Test
    @DisplayName("Filter by an explicit list of internal ids")
    void filtersByExplicitInternalIds() {
        Issue first = givenIssue();
        Issue second = givenIssue();
        Issue excluded = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .iids(List.of(first.iid(), second.iid()))
                .build());

        assertThat(found).extracting(Issue::iid)
                .containsExactlyInAnyOrder(first.iid(), second.iid())
                .doesNotContain(excluded.iid());
    }

    @Test
    @DisplayName("Ignore unknown internal ids in the iids filter rather than 404ing")
    void ignoresUnknownInternalIdsInTheIidsFilter() {
        Issue known = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .iids(List.of(known.iid(), TestData.UNKNOWN_IID))
                .build());

        // An unmatched id is skipped, not an error - so a partial match must not 404.
        assertThat(found).extracting(Issue::iid).containsExactly(known.iid());
    }

    @Test
    @DisplayName("Filter by a creation-time window")
    void filtersByCreationWindow() {
        Instant before = Instant.now().minusSeconds(5);
        Issue recent = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .createdAfter(before)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid).contains(recent.iid());
        assertThat(found).allSatisfy(issue ->
                assertThat(issue.createdAt()).isAfterOrEqualTo(before));
    }

    @Test
    @DisplayName("Exclude issues created after the upper bound")
    void excludesIssuesCreatedAfterTheUpperBound() {
        Issue recent = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .createdBefore(recent.createdAt().minusSeconds(1))
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid).doesNotContain(recent.iid());
    }

    @Test
    @DisplayName("Filter by absence of a due date")
    void filtersByAbsenceOfDueDate() {
        Issue withoutDueDate = givenIssue();
        Issue withDueDate = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("due"))
                .dueDate(LocalDate.now().plusDays(5))
                .build());

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .dueDate(com.qa.gitlab.model.DueDateFilter.NO_DUE_DATE)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(withoutDueDate.iid())
                .doesNotContain(withDueDate.iid());
    }

    @Test
    @DisplayName("Return only the caller's own issues for scope=created_by_me")
    void returnsOnlyIssuesCreatedByTheCallerForCreatedByMeScope() {
        Issue mine = givenIssue();
        long selfId = mine.author().id();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .scope(IssueScope.CREATED_BY_ME)
                .perPage(IssueQuery.MAX_PER_PAGE)
                .build());

        assertThat(found)
                .isNotEmpty()
                .allSatisfy(issue -> assertThat(issue.author().id()).isEqualTo(selfId));
    }

    @Test
    @DisplayName("Sort by creation date ascending")
    void sortsByCreationDateAscending() {
        String marker = TestData.uniqueLabel();
        Issue first = givenIssue(labelled(marker));
        Issue second = givenIssue(labelled(marker));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .orderBy(IssueOrderBy.CREATED_AT)
                .sort(SortDirection.ASC)
                .build());

        assertThat(found).extracting(Issue::iid).containsExactly(first.iid(), second.iid());
    }

    @Test
    @DisplayName("Sort by creation date descending by default")
    void sortsByCreationDateDescendingByDefault() {
        String marker = TestData.uniqueLabel();
        Issue first = givenIssue(labelled(marker));
        Issue second = givenIssue(labelled(marker));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .build());

        // Documented default is created_at desc; asserting it guards against a silent change.
        assertThat(found).extracting(Issue::iid).containsExactly(second.iid(), first.iid());
    }

    @Test
    @DisplayName("Sort by title")
    void sortsByTitle() {
        String marker = TestData.uniqueLabel();
        Issue zebra = givenIssue(titledAndLabelled("zzz-" + TestData.uniqueTitle(), marker));
        Issue apple = givenIssue(titledAndLabelled("aaa-" + TestData.uniqueTitle(), marker));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .orderBy(IssueOrderBy.TITLE)
                .sort(SortDirection.ASC)
                .build());

        assertThat(found).extracting(Issue::iid).containsExactly(apple.iid(), zebra.iid());
    }

    @Test
    @DisplayName("Combine multiple filters conjunctively")
    void combinesFiltersConjunctively() {
        String marker = TestData.uniqueLabel();
        Issue match = givenIssue(labelled(marker));
        Issue wrongLabel = givenIssue();

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .state(IssueStateFilter.OPENED)
                .confidential(false)
                .build());

        assertThat(found).extracting(Issue::iid)
                .contains(match.iid())
                .doesNotContain(wrongLabel.iid());
    }

    @Test
    @DisplayName("Return an empty list for contradictory filters")
    void returnsEmptyListForContradictoryFilters() {
        String marker = TestData.uniqueLabel();
        givenIssue(labelled(marker));

        List<Issue> found = issues.list(projectId(), IssueQuery.builder()
                .labels(List.of(marker))
                .not(Map.of("labels", marker))
                .build());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("List issues by URL-encoded project path")
    void listsIssuesByUrlEncodedProjectPath() {
        String marker = TestData.uniqueLabel();
        Issue tagged = givenIssue(labelled(marker));

        // Same filter, project addressed by path instead of id - the filters must apply identically
        // whichever spelling of :id was used.
        Response response = issues.listRaw(projectPath(), IssueQuery.builder()
                .labels(List.of(marker))
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        response.then().body(Schemas.issueList());
        assertThat(List.of(response.as(Issue[].class))).extracting(Issue::iid)
                .containsExactly(tagged.iid());
    }

    // ---------- every documented value of each closed set ----------

    // The four sweeps below complement the semantic filter tests above rather than repeating them.
    // A filter test proves one value behaves correctly; these prove the *whole documented set* is
    // still accepted. Before them, 11 of the 20 values across these four enums were never sent
    // once - so a value GitLab renamed or dropped would have failed nothing. Asserting the schema
    // as well as the status means a value that is accepted but changes the response shape also fails.

    @ParameterizedTest(name = "state={0} is accepted")
    @EnumSource(IssueStateFilter.class)
    @DisplayName("Accept every documented state filter value")
    void acceptsEveryDocumentedStateValue(IssueStateFilter state) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .state(state)
                .perPage(1)
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        response.then().body(Schemas.issueList());
    }

    @ParameterizedTest(name = "order_by={0} is accepted")
    @EnumSource(IssueOrderBy.class)
    @DisplayName("Accept every documented order_by value")
    void acceptsEveryDocumentedOrderByValue(IssueOrderBy orderBy) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .orderBy(orderBy)
                .sort(SortDirection.DESC)
                .perPage(1)
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        response.then().body(Schemas.issueList());
    }

    @ParameterizedTest(name = "scope={0} is accepted")
    @EnumSource(IssueScope.class)
    @DisplayName("Accept every documented scope value")
    void acceptsEveryDocumentedScopeValue(IssueScope scope) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .scope(scope)
                .perPage(1)
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        response.then().body(Schemas.issueList());
    }

    @ParameterizedTest(name = "due_date={0} is accepted")
    @EnumSource(com.qa.gitlab.model.DueDateFilter.class)
    @DisplayName("Accept every documented due_date shortcut")
    void acceptsEveryDocumentedDueDateShortcut(com.qa.gitlab.model.DueDateFilter dueDate) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .dueDate(dueDate)
                .perPage(1)
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        response.then().body(Schemas.issueList());
    }

    private static CreateIssueRequest labelled(String... labels) {
        return CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(String.join(",", labels))
                .build();
    }

    private static CreateIssueRequest titledAndLabelled(String title, String label) {
        return CreateIssueRequest.builder()
                .title(title)
                .labels(label)
                .build();
    }
}
