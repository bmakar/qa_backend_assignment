package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.model.StateEvent;
import com.qa.gitlab.model.UpdateIssueRequest;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting an issue requires Owner or administrator rights on the project. A Maintainer receives
 * 403, so on gitlab.com these tests need a project in a namespace you own.
 * <p>
 * Error cases for this endpoint live in {@link IssueDeleteNegativeTest}.
 */
@DisplayName("DELETE /projects/:id/issues/:iid")
class IssueDeleteTest extends ApiTest {

    @Test
    @DisplayName("Delete an issue and make it unreachable afterwards")
    void deletesIssueAndMakesItUnreachable() {
        Issue created = givenIssue();

        issues.delete(projectId(), created.iid());
        untrack(created);

        assertThat(issues.getRaw(projectId(), created.iid()).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Return 204 with an empty body")
    void returnsNoContentWithAnEmptyBody() {
        Issue created = givenIssue();

        var response = issues.deleteRaw(projectId(), created.iid());
        untrack(created);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body().asString()).isEmpty();
    }

    @Test
    @DisplayName("Remove a deleted issue from the listing")
    void deletedIssueDisappearsFromListing() {
        Issue created = givenIssue();

        issues.delete(projectId(), created.iid());
        untrack(created);

        assertThat(issues.list(projectId(), IssueQuery.allStatesMaxPage()))
                .extracting(Issue::iid)
                .doesNotContain(created.iid());
    }

    @Test
    @DisplayName("Delete a closed issue")
    void deletesClosedIssue() {
        Issue created = givenIssue();
        issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        issues.delete(projectId(), created.iid());
        untrack(created);

        assertThat(issues.getRaw(projectId(), created.iid()).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Delete a confidential issue")
    void deletesConfidentialIssue() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("secret"))
                .confidential(true)
                .build());

        issues.delete(projectId(), created.iid());
        untrack(created);

        assertThat(issues.getRaw(projectId(), created.iid()).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Leave other issues intact when one is deleted")
    void deletingOneIssueLeavesOthersIntact() {
        Issue doomed = givenIssue();
        Issue survivor = givenIssue();

        issues.delete(projectId(), doomed.iid());
        untrack(doomed);

        assertThat(issues.getRaw(projectId(), survivor.iid()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Do not reuse a deleted issue's internal id")
    void deletingDoesNotReuseTheInternalId() {
        Issue deleted = givenIssue();
        issues.delete(projectId(), deleted.iid());
        untrack(deleted);

        Issue next = givenIssue();

        // The iid counter must not roll back into a freed slot, or references to the deleted issue
        // would silently start resolving to a different one.
        assertThat(next.iid()).isGreaterThan(deleted.iid());
    }
}
