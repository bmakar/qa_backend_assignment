package com.qa.gitlab.tests;

import com.qa.gitlab.model.Issue;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not-found and cross-project cases for {@code GET /projects/:id/issues/:iid}. Split from
 * {@link IssueReadTest} so the happy path and the error path can each be read on their own.
 */
@DisplayName("GET /projects/:id/issues/:iid - not found")
class IssueReadNotFoundTest extends ApiTest {

    @Test
    @DisplayName("Do not resolve an issue by its global id in place of its internal id")
    void addressesIssuesByInternalIdNotGlobalId() {
        Issue created = givenIssue();

        // The single most common mistake against this API. The global id is not a valid path
        // segment here, so using it must not resolve to the same issue.
        Response byGlobalId = issues.getRaw(projectId(), created.id());

        assertThat(created.id()).isNotEqualTo(created.iid());
        if (byGlobalId.statusCode() == 200) {
            assertThat(byGlobalId.as(Issue.class).iid())
                    .as("the global id must never be interpreted as an iid")
                    .isNotEqualTo(created.iid());
        } else {
            assertThat(byGlobalId.statusCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("Return 404 for an unknown internal id")
    void returnsNotFoundForUnknownInternalId() {
        Response response = issues.getRaw(projectId(), TestData.UNKNOWN_IID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("message")).contains("404");
    }

    @Test
    @DisplayName("Return 404 for a zero or negative internal id")
    void returnsNotFoundForZeroAndNegativeInternalIds() {
        // GitLab routes :iid as a positive integer, so these never reach the lookup.
        assertThat(issues.getRaw(projectId(), 0L).statusCode()).isEqualTo(404);
        assertThat(issues.getRaw(projectId(), -1L).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Return 404 for an unknown project")
    void returnsNotFoundForUnknownProject() {
        Response response = issues.getRaw(TestData.UNKNOWN_PROJECT_ID, 1L);

        assertThat(response.statusCode()).isEqualTo(404);
    }
}
