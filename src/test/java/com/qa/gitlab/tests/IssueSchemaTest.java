package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.Schemas;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Response structure, validated against a JSON Schema.
 * <p>
 * This suite covers the gap the typed models leave open. {@code FAIL_ON_UNKNOWN_PROPERTIES} is
 * disabled so the suite survives GitLab adding fields, and Jackson maps a missing field to
 * {@code null} rather than failing - which together mean a response that dropped {@code web_url},
 * or started returning {@code iid} as a string, would deserialize cleanly and pass every
 * model-based assertion in the project.
 * <p>
 * The schemas therefore assert types and required fields but deliberately do not forbid additional
 * properties: the goal is to catch the contract narrowing, not to break the build every time GitLab
 * ships a feature.
 * <p>
 * The error-response schema lives in {@link IssueErrorSchemaTest}.
 */
@DisplayName("Response schema contract")
class IssueSchemaTest extends ApiTest {

    @Test
    @DisplayName("Match the issue schema on a minimal create response")
    void createResponseMatchesIssueSchema() {
        Issue tracked = givenIssue();

        issues.getRaw(projectId(), tracked.iid())
                .then().body(Schemas.issue());
    }

    @Test
    @DisplayName("Match the issue schema when every field is populated")
    void fullyPopulatedIssueMatchesIssueSchema() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("schema check")
                .labels(TestData.uniqueLabel())
                .confidential(true)
                .dueDate(LocalDate.now().plusDays(4))
                .build());

        issues.getRaw(projectId(), created.iid())
                .then().body(Schemas.issue());
    }

    @Test
    @DisplayName("Match the collection schema on a list response")
    void listResponseMatchesCollectionSchema() {
        givenIssue();

        issues.listRaw(projectId(), IssueQuery.builder().perPage(5).build())
                .then().body(Schemas.issueList());
    }

    @Test
    @DisplayName("Keep a single-result list as an array, not a bare object")
    void singleResultListIsStillAnArray() {
        String marker = TestData.uniqueLabel();
        givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(marker)
                .build());

        // A single result must not collapse into a bare object - the schema's array type catches
        // that, where a lenient client would silently cope.
        issues.listRaw(projectId(), IssueQuery.builder().labels(List.of(marker)).build())
                .then().body(Schemas.issueList());
    }

    @Test
    @DisplayName("Keep an empty result an array, not a 404 or a null body")
    void emptyResultIsStillAnArray() {
        issues.listRaw(projectId(), IssueQuery.builder()
                        .search("nothing-matches-" + TestData.uniqueLabel())
                        .build())
                .then().body(Schemas.issueList());
    }
}
