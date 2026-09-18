package com.qa.gitlab.testkit;

import com.qa.gitlab.client.GitLabClient;
import com.qa.gitlab.client.IssuesApi;
import com.qa.gitlab.client.UsersApi;
import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared fixture for issue tests: the API under test, the target project, and teardown.
 * <p>
 * The tracking list is an <em>instance</em> field on purpose. JUnit's default lifecycle creates a
 * new test instance per test method, so each test gets its own list with no synchronization and no
 * leakage between tests - which is what makes {@code parallel.mode.classes.default=concurrent}
 * safe here.
 * <p>
 * Issues are torn down newest-first.
 */
@Tag(Tags.API)
public abstract class ApiTest {

    /**
     * Attaches request/response bodies to the Allure report. One instance, reused at every call
     * site below rather than reconstructed per request.
     * <p>
     * {@code GitLabClient} lives in {@code src/main} and cannot import this filter - it is in
     * {@code src/test}. So the filter is chained onto the {@link io.restassured.specification.
     * RequestSpecification} {@code GitLabClient} returns, once per entry point that needs it,
     * rather than wired in centrally. Three call sites means three places to touch if this filter
     * is ever replaced - accepted on purpose so {@code GitLabClient} stays a plain request builder
     * with nothing to explain about how test reporting reaches it.
     */
    private static final AllureAttachmentFilter ALLURE = new AllureAttachmentFilter();

    protected final IssuesApi issues = new IssuesApi(() -> GitLabClient.spec().filter(ALLURE));

    private final UsersApi users = new UsersApi(() -> GitLabClient.spec().filter(ALLURE));

    private final Deque<Long> trackedIids = new ArrayDeque<>();

    protected long projectId() {
        return TestProject.id();
    }

    /** The project's unencoded {@code path_with_namespace}, for path-addressed requests. */
    protected String projectPath() {
        return TestProject.path();
    }

    /**
     * The id of the user the configured token authenticates as.
     * <p>
     * One {@code GET /user} call, not a disposable issue created and torn down just to read its
     * author back. Not cached: every current caller reads it once, and a test instance is
     * per-method anyway, so memoizing here would save nothing while adding a second thing to reason
     * about.
     */
    protected long selfId() {
        return users.current().id();
    }

    /**
     * Highest {@code iid} currently in the project - the default listing sort is newest first.
     * <p>
     * For tests that request an explicit {@code iid} on create: asking for a value above this one
     * makes a honoured request distinguishable from the value auto-assignment would have produced
     * anyway. Callers should add a margin, so that issues other classes create concurrently between
     * this call and theirs cannot collide with the value they picked.
     */
    protected long highestExistingIid() {
        List<Issue> newest = issues.list(projectId(), IssueQuery.builder().perPage(1).build());
        return newest.isEmpty() ? 0L : newest.get(0).iid();
    }

    /** Creates an issue and registers it for deletion after the test. */
    protected Issue givenIssue(CreateIssueRequest request) {
        Issue issue = issues.create(projectId(), request);
        deleteAfterTest(issue);
        return issue;
    }

    /** Creates a minimal issue and registers it for deletion after the test. */
    protected Issue givenIssue() {
        return givenIssue(TestData.minimalIssue());
    }

    /** Creates {@code count} minimal issues, all registered for deletion. */
    protected List<Issue> givenIssues(int count) {
        return Stream.generate(this::givenIssue).limit(count).toList();
    }

    /**
     * Sends a raw create and registers anything that got created, whatever the status.
     * <p>
     * Every negative create test must go through this rather than {@code issues.createRaw}. The
     * reason is concrete: {@code rejectsMalformedDueDate} expected six 400s, GitLab answered six
     * 201s, and because the test only asserted the status it left six issues behind permanently.
     * A test that asserts a request is rejected has to clean up for the case where it was not -
     * that is exactly when the assumption behind the test was wrong.
     */
    protected Response createRawTracked(Object body) {
        return trackIfCreated(issues.createRaw(projectId(), body));
    }

    /**
     * Registers the issue in a 201 response for deletion, and passes the response through.
     * <p>
     * Use this on every path that might create something unintentionally. Cleanup keyed off an
     * expected status is cleanup that skips the one case where it was needed.
     */
    protected Response trackIfCreated(Response response) {
        if (response.statusCode() == 201) {
            deleteAfterTest(response.as(Issue.class));
        }
        return response;
    }

    /** The endpoint as an anonymous caller, for authentication coverage. */
    protected IssuesApi anonymousIssues() {
        return new IssuesApi(() -> GitLabClient.anonymous().filter(ALLURE));
    }

    /** The endpoint with a specific token, for authentication coverage. */
    protected IssuesApi issuesWithToken(String token) {
        return new IssuesApi(() -> GitLabClient.withToken(token).filter(ALLURE));
    }

    protected void deleteAfterTest(Issue issue) {
        trackedIids.push(issue.iid());
    }

    /**
     * Drops a single issue from teardown, for tests that delete it themselves. Teardown tolerates a
     * 404 anyway, but untracking keeps the intent explicit and the logs clean.
     */
    protected void untrack(Issue issue) {
        trackedIids.remove(issue.iid());
    }

    @AfterEach
    final void deleteTrackedIssues() {
        long projectId = projectId();
        while (!trackedIids.isEmpty()) {
            issues.deleteQuietly(projectId, trackedIids.pop());
        }
    }
}
