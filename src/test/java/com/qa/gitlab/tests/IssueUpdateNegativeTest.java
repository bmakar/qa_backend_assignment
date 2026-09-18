package com.qa.gitlab.tests;

import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.UpdateIssueRequest;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error cases for {@code PUT /projects/:id/issues/:iid}. Split from {@link IssueUpdateTest} so the
 * happy path and the error path can each be read on their own.
 */
@DisplayName("PUT /projects/:id/issues/:iid - errors")
class IssueUpdateNegativeTest extends ApiTest {

    @Test
    @DisplayName("Return 404 for an unknown internal id")
    void returnsNotFoundForUnknownInternalId() {
        Response response = issues.updateRaw(projectId(), TestData.UNKNOWN_IID,
                UpdateIssueRequest.builder().title(TestData.uniqueTitle()).build());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Reject clearing the mandatory title to blank")
    void rejectsTitleClearedToBlank() {
        Issue created = givenIssue();

        Response response = issues.updateRaw(projectId(), created.iid(),
                java.util.Map.of("title", ""));

        // title is mandatory, so it must not be possible to clear it through an update.
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(issues.get(projectId(), created.iid()).title()).isEqualTo(created.title());
    }
}
