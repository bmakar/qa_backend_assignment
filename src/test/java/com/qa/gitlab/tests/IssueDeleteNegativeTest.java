package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.model.IssueStateFilter;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error cases for {@code DELETE /projects/:id/issues/:iid}. Split from {@link IssueDeleteTest} so
 * the happy path and the error path can each be read on their own.
 */
@DisplayName("DELETE /projects/:id/issues/:iid - errors")
class IssueDeleteNegativeTest extends ApiTest {

    @Test
    @DisplayName("Return 404 on a second delete of the same issue")
    void secondDeleteReturnsNotFound() {
        Issue created = givenIssue();
        issues.delete(projectId(), created.iid());
        untrack(created);

        // Not idempotent in the 204-every-time sense: the second call reports the resource is gone.
        assertThat(issues.deleteRaw(projectId(), created.iid()).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Return 404 for an unknown internal id")
    void returnsNotFoundForUnknownInternalId() {
        assertThat(issues.deleteRaw(projectId(), TestData.UNKNOWN_IID).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Return 404 for an unknown project")
    void returnsNotFoundForUnknownProject() {
        assertThat(issues.deleteRaw(TestData.UNKNOWN_PROJECT_ID, 1L).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Leave the listing unchanged after a failed delete")
    void listingIsUnchangedByAFailedDelete() {
        // Scoped to a label this test owns, rather than phrased against the whole collection.
        //
        // The previous version compared two unfiltered allStatesMaxPage() listings and asserted the
        // second still contained everything in the first. That is unsound twice over: the query
        // returns only the newest 100 issues, so it is a sliding window that any concurrently
        // created issue shifts items off the end of, and it makes the result depend on data every
        // other test class owns. It failed exactly that way - two issues created elsewhere pushed
        // the oldest one off page 1, and another class deleted one of its own mid-test - reporting
        // a delete bug that did not exist.
        String marker = TestData.uniqueLabel();
        Issue first = givenIssue(labelled(marker));
        Issue second = givenIssue(labelled(marker));

        assertThat(issues.deleteRaw(projectId(), TestData.UNKNOWN_IID).statusCode()).isEqualTo(404);

        // Stronger than the original as well as deterministic: not merely that nothing vanished, but
        // that exactly the issues this test created are still present - none deleted, none added.
        assertThat(issues.list(projectId(), IssueQuery.builder()
                        .labels(List.of(marker))
                        .state(IssueStateFilter.ALL)
                        .perPage(IssueQuery.MAX_PER_PAGE)
                        .build()))
                .extracting(Issue::iid)
                .containsExactlyInAnyOrder(first.iid(), second.iid());
    }

    private static CreateIssueRequest labelled(String marker) {
        return CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels(marker)
                .build();
    }
}
