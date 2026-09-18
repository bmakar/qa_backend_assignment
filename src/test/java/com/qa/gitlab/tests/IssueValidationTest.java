package com.qa.gitlab.tests;

import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.Schemas;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error contract for invalid requests.
 * <p>
 * These tests are why {@code IssuesApi} exposes {@code *Raw} methods - the typed methods assert a
 * success status and would fail before the response could be inspected. Deliberately invalid
 * parameter values go through {@code IssueQuery.raw}, so the typed query surface never has to accept
 * garbage just to let a negative test exist.
 */
@DisplayName("Issues API error handling")
class IssueValidationTest extends ApiTest {

    static Stream<Map<String, Object>> bodiesWithoutTitle() {
        // Explicit type witnesses: Map.of() with mixed value types would otherwise infer
        // Map<String, Serializable & Comparable<...>> and not match the declared Stream type.
        return Stream.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("description", "no title supplied"),
                Map.<String, Object>of("labels", "bug", "confidential", true));
    }

    @ParameterizedTest(name = "Payload without title is rejected: {0}")
    @MethodSource("bodiesWithoutTitle")
    @DisplayName("Reject a create request without a title")
    void rejectsCreateWithoutTitle(Map<String, Object> body) {
        Response response = issues.createRaw(projectId(), body);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Name the offending field in the 400 body when the title is missing")
    void namesTheOffendingFieldWhenTitleIsMissing() {
        Response response = issues.createRaw(projectId(), Map.of("description", "no title"));

        // A 400 that does not say what was wrong is a poor contract; asserting the field name keeps
        // the error message part of the tested surface.
        assertThat(response.body().asString()).contains("title");
        response.then().body(Schemas.error());
    }

    @Test
    @DisplayName("Report an unknown project as 404")
    void reportsUnknownProjectAsNotFound() {
        Response response = issues.listRaw(TestData.UNKNOWN_PROJECT_ID, IssueQuery.none());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @ParameterizedTest(name = "Unsupported state filter is rejected: {0}")
    @ValueSource(strings = {"not-a-real-state", "OPENED", "open", "any"})
    @DisplayName("Reject an unsupported state filter")
    void rejectsUnsupportedStateFilter(String state) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("state", state))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @ParameterizedTest(name = "Unsupported sort direction is rejected: {0}")
    @ValueSource(strings = {"sideways", "ascending", "ASC "})
    @DisplayName("Reject an unsupported sort direction")
    void rejectsUnsupportedSortDirection(String sort) {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("sort", sort))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject an unsupported order_by field")
    void rejectsUnsupportedOrderByField() {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("order_by", "not_a_column"))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject an unsupported scope value")
    void rejectsUnsupportedScope() {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("scope", "everyones"))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject an unsupported issue_type value")
    void rejectsUnsupportedIssueType() {
        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("issue_type", "not_a_type"))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject a non-numeric project id")
    void rejectsNonNumericProjectId() {
        // Routed through IssuesApi's path-addressed overload rather than a bare REST Assured call,
        // so this request still picks up the Allure attachment filter every other test gets - a raw
        // call built outside ApiTest's wiring produces no request/response attachment on failure.
        Response response = issues.listRaw("not-a-number", IssueQuery.none());

        assertThat(response.statusCode()).isIn(400, 404);
    }

    @ParameterizedTest(name = "issue_type {0} is rejected on create")
    @ValueSource(strings = {"ISSUE", "INCIDENT", "TASK", "story", "test_case-1", "123"})
    @DisplayName("Reject a create issue_type that is not an exact lowercase match")
    void rejectsCreateIssueTypeThatIsNotAnExactMatch(String issueType) {
        // The uppercase cases are the interesting ones: the *response* returns issue_type lowercased
        // but a caller reading the enum from documentation, or round-tripping a value from some other
        // GitLab field that is uppercased, sends the uppercase form. The existing coverage only
        // rejected an unsupported *filter* value, never a create payload.
        Response response = createRawTracked(
                Map.of("title", TestData.uniqueTitle(), "issue_type", issueType));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @ParameterizedTest(name = "severity {0} is rejected on create")
    @ValueSource(strings = {"UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL", "urgent", "123"})
    @DisplayName("Reject a create severity that is not an exact lowercase match")
    void rejectsCreateSeverityThatIsNotAnExactMatch(String severity) {
        // Severity is the one field where request and response disagree on case - the response
        // returns "HIGH" while only "high" is accepted on the way in. Sending back what was read is
        // therefore an easy mistake, and it must fail loudly rather than be silently ignored.
        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "issue_type", "incident",
                "severity", severity));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @ParameterizedTest(name = "Non-numeric iid {0} is invalid, not merely absent")
    @ValueSource(strings = {"0b23", "abc"})
    @DisplayName("Reject a non-numeric issue iid as invalid rather than reporting it not found")
    void rejectsNonNumericIssueIid(String iid) {
        // Distinct from an unknown-but-numeric iid, which is a 404: a value that is not an integer at
        // all never reaches the lookup, so it is a malformed request rather than a missing resource.
        // Conflating the two hides a client bug behind a plausible-looking 404.
        //
        // "1.5" is deliberately not included here: confirmed against a live GET that GitLab truncates
        // it to iid=1 and returns 200 rather than rejecting it, unlike "abc" or "0b23" - a decimal
        // point is not "not an integer" to this endpoint, it is an integer plus noise it discards.
        //
        // Routed through IssuesApi.getRaw(long, String) rather than a bare REST Assured call, so this
        // request still picks up the Allure attachment filter - a raw call built outside ApiTest's
        // wiring produces no request/response attachment on failure.
        Response response = issues.getRaw(projectId(), iid);

        assertThat(response.statusCode())
                .as("a non-integer iid should be a 400, not a 404 that looks like a missing issue")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("Reject an update request carrying no updatable field")
    void rejectsUpdateWithNoUpdatableField() {
        Issue created = givenIssue();

        Response response = issues.updateRaw(projectId(), created.iid(), Map.of());

        // An update that changes nothing is a caller mistake worth surfacing, not a no-op success -
        // otherwise a client whose field name was wrong gets a 200 and assumes the change landed.
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Reject a create that reuses an existing iid")
    void rejectsCreateWithDuplicateIid() {
        Issue existing = givenIssue();

        Response response = createRawTracked(Map.of(
                "title", TestData.uniqueTitle(),
                "iid", existing.iid()));

        // Two issues sharing an iid would make the per-project identifier ambiguous and every URL
        // built from it wrong, so this is the one iid case that must not succeed quietly. 409 is the
        // documented answer; 400 would also be defensible, which is why both are accepted.
        assertThat(response.statusCode())
                .as("a duplicate iid must be refused, not silently reassigned")
                .isIn(400, 409);
    }

    @ParameterizedTest(name = "created_at {0} is rejected")
    @ValueSource(strings = {"not-a-date", "2026-02-30T10:00:00Z", "2026-09-04T25:00:00Z"})
    @DisplayName("Reject a malformed created_at rather than silently dropping it")
    void rejectsMalformedCreatedAt(String createdAt) {
        Response response = createRawTracked(
                Map.of("title", TestData.uniqueTitle(), "created_at", createdAt));

        // Worth contrasting with FINDING-1: a malformed due_date or start_date is accepted with a 201
        // and silently discarded, while a malformed created_at is rejected outright. Same endpoint,
        // same class of input, opposite handling - so this test and the boundary-suite date tests
        // pin the asymmetry from both sides.
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Report an unknown project path as 404")
    void reportsUnknownProjectPathAsNotFound() {
        // The path-addressed counterpart of reportsUnknownProjectAsNotFound. Passed unencoded, so
        // REST Assured encodes the slash; a 404 here is the project not existing, not the encoding
        // being wrong.
        Response response = issues.listRaw("no-such-group/no-such-project", IssueQuery.none());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Reject an update on an unknown project")
    void rejectsUpdateOnUnknownProject() {
        Response response = issues.updateRaw(TestData.UNKNOWN_PROJECT_ID, 1L,
                Map.of("title", "irrelevant"));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Return error responses as JSON, not HTML")
    void errorResponsesAreJson() {
        Response response = issues.getRaw(projectId(), TestData.UNKNOWN_IID);

        // An error that comes back as an HTML page breaks every client that parses the body.
        assertThat(response.contentType()).contains("application/json");
        response.then().body(Schemas.error());
    }

    @Test
    @DisplayName("Do not return a server error for any malformed filter value")
    void doesNotReturnServerErrorForAnyMalformedFilter() {
        // A 4xx is a contract; a 5xx is a defect. This sweeps the filters most likely to be
        // mishandled and asserts none of them can crash the endpoint.
        Map<String, Object> hostileFilters = Map.of(
                "state", "'; DROP TABLE issues; --",
                "labels", "\u0000",
                "author_id", "not-a-number",
                "created_after", "not-a-date",
                "iids[]", "not-a-number");

        hostileFilters.forEach((name, value) -> {
            Response response = issues.listRaw(projectId(), IssueQuery.builder()
                    .raw(Map.of(name, value))
                    .build());
            assertThat(response.statusCode())
                    .as("filter %s=%s must not cause a server error", name, value)
                    .isLessThan(500);
        });
    }

    /**
     * One wrong-typed value per create field: a number where a string is documented, a string where a
     * boolean is, an array where a scalar is.
     * <p>
     * These go through a raw {@code Map} because {@code CreateIssueRequest} is typed and cannot
     * express them - which is the point. A caller with a hand-rolled client or a loosely typed
     * language sends exactly these by accident.
     */
    static Stream<Arguments> wrongTypedCreateFields() {
        return Stream.of(
                Arguments.of("title", 999),
                Arguments.of("description", 42),
                Arguments.of("confidential", "yes"),
                Arguments.of("labels", 123),
                Arguments.of("assignee_ids", "not-a-list"),
                Arguments.of("milestone_id", "not-a-number"),
                Arguments.of("issue_type", List.of("issue")),
                Arguments.of("severity", 5),
                Arguments.of("due_date", true),
                Arguments.of("start_date", 20261231),
                Arguments.of("weight", "heavy"),
                Arguments.of("iid", List.of(1)),
                Arguments.of("created_at", 12345));
    }

    @ParameterizedTest(name = "Wrong-typed {0}={1} is handled without a server error")
    @MethodSource("wrongTypedCreateFields")
    @DisplayName("Handle a create field of the wrong JSON type without a server error")
    void handlesWrongTypedCreateField(String field, Object wrongValue) {
        // A valid title first, then the wrong-typed field on top - so only the one field under test
        // is at fault. When the field under test *is* title, it overwrites the valid one, which is
        // why this is a mutable map rather than Map.of.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", TestData.uniqueTitle());
        body.put(field, wrongValue);

        // createRawTracked, not createRaw: if the value is coerced rather than rejected then an
        // issue was created, and a test asserting rejection has to clean up for the case where the
        // assumption behind it was wrong.
        Response response = createRawTracked(body);

        // The body-side mirror of doesNotReturnServerErrorForAnyMalformedFilter above. Deliberately
        // not asserting 400: GitLab's parameter layer rejects some of these and coerces others, and
        // which is which has not been observed per field - pinning a guess is how a test starts
        // asserting fiction. Both invariants below hold either way.
        assertThat(response.statusCode())
                .as("create field %s=%s must not crash the endpoint", field, wrongValue)
                .isLessThan(500);

        if (response.statusCode() == 201) {
            // Accepted rather than refused. A coerced value is defensible; a response whose shape
            // changed because a wrong-typed value was stored verbatim is not - so the issue must
            // still satisfy the documented contract.
            response.then().body(Schemas.issue());
        }
    }
}
