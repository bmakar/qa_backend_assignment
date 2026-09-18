package com.qa.gitlab.tests;

import com.qa.gitlab.client.IssuesApi;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.Schemas;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentication and authorization.
 * <p>
 * This is the coverage area most commonly missing from an API suite, because the happy path needs a
 * valid token and it is easy to stop there. It matters disproportionately: an endpoint that leaks a
 * private project to an anonymous caller is a far worse defect than one that mishandles a due date.
 * <p>
 * Note the deliberate assertion on <b>404 rather than 403</b> for a project the caller cannot see.
 * Answering 403 would confirm the project exists, so GitLab returns 404 - hiding existence is the
 * correct behaviour and a test that expected 403 would be asserting a vulnerability.
 */
@DisplayName("Issues API authentication and authorization")
class IssueAuthorizationTest extends ApiTest {

    @Test
    @DisplayName("Reject an anonymous listing request")
    void rejectsAnonymousListing() {
        Response response = anonymousIssues().listRaw(projectId(), IssueQuery.none());

        // A private project must not be readable without credentials. 404 is also acceptable here,
        // and is in fact preferable, because it does not confirm the project exists.
        assertThat(response.statusCode()).isIn(401, 404);
    }

    @Test
    @DisplayName("Reject an anonymous read request")
    void rejectsAnonymousRead() {
        Issue created = givenIssue();

        Response response = anonymousIssues().getRaw(projectId(), created.iid());

        assertThat(response.statusCode()).isIn(401, 404);
    }

    @Test
    @DisplayName("Reject an anonymous create request")
    void rejectsAnonymousCreate() {
        Response response = anonymousIssues()
                .createRaw(projectId(), Map.of("title", TestData.uniqueTitle()));

        assertThat(response.statusCode()).isIn(401, 403, 404);
    }

    @Test
    @DisplayName("Reject an anonymous delete request without deleting the issue")
    void rejectsAnonymousDelete() {
        Issue created = givenIssue();

        Response response = anonymousIssues().deleteRaw(projectId(), created.iid());

        assertThat(response.statusCode()).isIn(401, 403, 404);
        // The issue must still be there afterwards - a rejected delete that deleted anyway would be
        // the worst possible outcome and a status-code-only assertion would miss it.
        assertThat(issues.getRaw(projectId(), created.iid()).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "Malformed token is rejected: {0}")
    @ValueSource(strings = {
            "glpat-obviously-not-valid",
            "not-even-prefixed",
            "glpat-",
            "Bearer something",
            " "})
    @DisplayName("Reject a malformed token")
    void rejectsMalformedTokens(String token) {
        Response response = issuesWithToken(token).listRaw(projectId(), IssueQuery.none());

        // 404 belongs in this set for the same reason it does in rejectsAnonymousListing and
        // doesNotRevealExistenceOfAnInaccessibleProject: a token GitLab cannot authenticate leaves
        // the request effectively anonymous, and answering 401 or 403 for a *private* project would
        // confirm the project exists. Excluding 404 here made this test demand the one behaviour the
        // class javadoc calls a vulnerability. What matters is that access is refused - never 200.
        assertThat(response.statusCode()).isIn(401, 403, 404);
    }

    @Test
    @DisplayName("Return 401, not a 5xx, for a bad token")
    void returnsUnauthorizedNotServerErrorForABadToken() {
        IssuesApi unauthorised = issuesWithToken("glpat-not-a-real-token");

        Response response = unauthorised.listRaw(projectId(), IssueQuery.none());

        assertThat(response.statusCode()).isEqualTo(401);
        // A bad credential is a client error. A 5xx here would be a genuine defect.
        assertThat(response.statusCode()).isLessThan(500);
    }

    @Test
    @DisplayName("Match the error contract in the body of a rejected-auth response")
    void errorBodyForRejectedAuthMatchesTheContract() {
        Response response = issuesWithToken("glpat-not-a-real-token")
                .listRaw(projectId(), IssueQuery.none());

        response.then().body(Schemas.error());
    }

    @Test
    @DisplayName("Return 404, not 403, for an inaccessible project so existence is not revealed")
    void doesNotRevealExistenceOfAnInaccessibleProject() {
        // A project id that exists on gitlab.com but that this token has no access to is
        // indistinguishable from one that does not exist - by design.
        Response inaccessible = issues.listRaw(1L, IssueQuery.none());

        assertThat(inaccessible.statusCode())
                .as("403 would confirm the project exists; 404 must not")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Do not resolve an iid from one project against another")
    void doesNotLeakIssuesAcrossProjects() {
        Issue created = givenIssue();

        // The same iid queried against a different project must not resolve to our issue.
        Response other = issues.getRaw(TestData.UNKNOWN_PROJECT_ID, created.iid());

        assertThat(other.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Do not let an anonymous request see a confidential issue")
    void anonymousRequestDoesNotSeeConfidentialIssues() {
        Issue confidential = givenIssue(com.qa.gitlab.model.CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("secret"))
                .confidential(true)
                .build());

        Response response = anonymousIssues().getRaw(projectId(), confidential.iid());

        assertThat(response.statusCode()).isNotEqualTo(200);
    }
}
