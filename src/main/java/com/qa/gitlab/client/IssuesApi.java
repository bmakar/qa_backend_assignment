package com.qa.gitlab.client;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.model.UpdateIssueRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * The GitLab issues endpoint.
 * <p>
 * Every operation comes in two flavours, and this split is the single most important design
 * decision in the framework:
 * <ul>
 *   <li>the typed method asserts the documented success status and returns a model, so happy-path
 *       tests carry no status-code boilerplate;</li>
 *   <li>the {@code *Raw} method returns the {@link Response} untouched, so negative tests can
 *       assert on 4xx bodies at all.</li>
 * </ul>
 * Without it, either the happy path is cluttered or the error path is unreachable.
 */
public final class IssuesApi {

    private static final Logger LOG = LoggerFactory.getLogger(IssuesApi.class);

    private static final String COLLECTION = "/projects/{projectId}/issues";
    private static final String SINGLE = "/projects/{projectId}/issues/{iid}";

    private final Supplier<RequestSpecification> spec;

    public IssuesApi() {
        this(GitLabClient::spec);
    }

    /** Alternate credentials, for authentication and authorization tests. */
    public IssuesApi(Supplier<RequestSpecification> spec) {
        this.spec = spec;
    }

    // ---------- create ----------

    public Issue create(long projectId, CreateIssueRequest request) {
        return createRaw(projectId, request)
                .then().statusCode(201)
                .extract().as(Issue.class);
    }

    /** Body is {@code Object} so malformed payloads can be sent deliberately. */
    public Response createRaw(long projectId, Object body) {
        return spec.get()
                .pathParam("projectId", projectId)
                .body(body)
                .when().post(COLLECTION);
    }

    // ---------- read ----------

    public Issue get(long projectId, long iid) {
        return getRaw(projectId, iid)
                .then().statusCode(200)
                .extract().as(Issue.class);
    }

    public Response getRaw(long projectId, long iid) {
        return requestIssue(projectId, iid);
    }

    /**
     * Addresses the project by its path rather than its numeric id - the other half of what the API
     * documents for {@code :id}, which is "integer or string, the global ID or URL-encoded path".
     * <p>
     * Pass the path <em>unencoded</em>. REST Assured percent-encodes a path parameter, so a raw
     * {@code group/project} becomes {@code group%2Fproject}; pre-encoding it yields
     * {@code group%252Fproject} and a 404 that looks like a missing project rather than a
     * double-encoded one.
     */
    public Response getRaw(String projectPath, long iid) {
        return requestIssue(projectPath, iid);
    }

    /**
     * For an {@code iid} that must be rejected as malformed rather than looked up as a real one -
     * {@code long} cannot carry a non-numeric value, so this is the only way to send one through the
     * typed client instead of a raw REST Assured call that would bypass its Allure attachment.
     */
    public Response getRaw(long projectId, String rawIid) {
        return requestIssue(projectId, rawIid);
    }

    private Response requestIssue(Object project, Object iid) {
        return spec.get()
                .pathParam("projectId", project)
                .pathParam("iid", iid)
                .when().get(SINGLE);
    }

    public List<Issue> list(long projectId, IssueQuery query) {
        return Arrays.asList(listRaw(projectId, query)
                .then().statusCode(200)
                .extract().as(Issue[].class));
    }

    /** Listing plus the pagination headers, which carry most of the pagination contract. */
    public Page<Issue> listPage(long projectId, IssueQuery query) {
        Response response = listRaw(projectId, query);
        response.then().statusCode(200);
        return Page.from(response, Issue[].class);
    }

    public Response listRaw(long projectId, IssueQuery query) {
        return requestCollection(projectId, query);
    }

    /** Addresses the project by its unencoded path - see {@link #getRaw(String, long)}. */
    public Response listRaw(String projectPath, IssueQuery query) {
        return requestCollection(projectPath, query);
    }

    private Response requestCollection(Object project, IssueQuery query) {
        return spec.get()
                .pathParam("projectId", project)
                .queryParams(query.asQueryParams())
                .when().get(COLLECTION);
    }

    // ---------- update ----------

    public Issue update(long projectId, long iid, UpdateIssueRequest request) {
        return updateRaw(projectId, iid, request)
                .then().statusCode(200)
                .extract().as(Issue.class);
    }

    public Response updateRaw(long projectId, long iid, Object body) {
        return spec.get()
                .pathParam("projectId", projectId)
                .pathParam("iid", iid)
                .body(body)
                .when().put(SINGLE);
    }

    // ---------- delete ----------

    /** Requires Owner or administrator rights on the project; a Maintainer receives 403. */
    public void delete(long projectId, long iid) {
        deleteRaw(projectId, iid).then().statusCode(204);
    }

    public Response deleteRaw(long projectId, long iid) {
        return spec.get()
                .pathParam("projectId", projectId)
                .pathParam("iid", iid)
                .when().delete(SINGLE);
    }

    /**
     * Best-effort delete for teardown. Cleanup must never turn a passing test red, nor mask the
     * original failure of a failing one - so this logs and swallows.
     */
    public void deleteQuietly(long projectId, long iid) {
        try {
            int status = deleteRaw(projectId, iid).statusCode();
            if (status != 204 && status != 404) {
                LOG.warn("Cleanup of issue {} in project {} returned {}", iid, projectId, status);
            }
        } catch (RuntimeException e) {
            LOG.warn("Cleanup of issue {} in project {} failed: {}", iid, projectId, e.getMessage());
        }
    }
}
