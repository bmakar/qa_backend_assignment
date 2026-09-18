package com.qa.gitlab.client;

import com.qa.gitlab.model.Project;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Project lifecycle, used only to provide a scratch project when none is configured. */
public final class ProjectsApi {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsApi.class);

    public Project create(String name) {
        return GitLabClient.spec()
                .body(Map.of(
                        "name", name,
                        "path", name,
                        "visibility", "private",
                        "issues_enabled", true,
                        "initialize_with_readme", false))
                .when().post("/projects")
                .then().statusCode(201)
                .extract().as(Project.class);
    }

    /** Reads a project, so a caller can learn its {@code path_with_namespace}. */
    public Project get(long projectId) {
        return GitLabClient.spec()
                .pathParam("projectId", projectId)
                .when().get("/projects/{projectId}")
                .then().statusCode(200)
                .extract().as(Project.class);
    }

    public Response deleteRaw(long projectId) {
        return GitLabClient.spec()
                .pathParam("projectId", projectId)
                .when().delete("/projects/{projectId}");
    }

    public void deleteQuietly(long projectId) {
        try {
            int status = deleteRaw(projectId).statusCode();
            LOG.info("Scratch project {} deletion returned {}", projectId, status);
        } catch (RuntimeException e) {
            LOG.warn("Scratch project {} deletion failed: {}", projectId, e.getMessage());
        }
    }
}
