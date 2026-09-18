package com.qa.gitlab.testkit;

import com.qa.gitlab.client.ProjectsApi;
import com.qa.gitlab.config.Config;
import com.qa.gitlab.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the project the suite runs against, once per JVM.
 * <p>
 * Preferred mode is an existing project supplied via configuration: on gitlab.com that avoids the
 * project-creation rate limit and, when the project sits in your own personal namespace, it makes
 * you Owner - which is what the delete tests require.
 * <p>
 * If no project is configured a scratch project is created and removed on JVM exit.
 */
public final class TestProject {

    private static final Logger LOG = LoggerFactory.getLogger(TestProject.class);

    private static volatile Long resolvedId;

    private static volatile String resolvedPath;

    private TestProject() {
    }

    /**
     * The project's {@code path_with_namespace}, <em>unencoded</em>, for tests that address the
     * project by path instead of by id. Resolved once per JVM with one extra request, and only if
     * something asks for it.
     */
    public static String path() {
        String current = resolvedPath;
        if (current != null) {
            return current;
        }
        synchronized (TestProject.class) {
            if (resolvedPath == null) {
                resolvedPath = new ProjectsApi().get(id()).pathWithNamespace();
                LOG.info("Project {} resolves to path {}", resolvedId, resolvedPath);
            }
            return resolvedPath;
        }
    }

    public static long id() {
        Long current = resolvedId;
        if (current != null) {
            return current;
        }
        synchronized (TestProject.class) {
            if (resolvedId == null) {
                resolvedId = Config.projectId().orElseGet(TestProject::createScratchProject);
                LOG.info("Tests will run against project {}", resolvedId);
            }
            return resolvedId;
        }
    }

    private static long createScratchProject() {
        ProjectsApi projects = new ProjectsApi();
        Project project = projects.create(TestData.uniqueTitle("project"));
        LOG.info("Created scratch project {} ({})", project.id(), project.webUrl());

        // A shutdown hook is enough here: the project is per-JVM, so there is no per-test hook to
        // attach it to, and leaking one private project on an aborted run is cheap and visible.
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> projects.deleteQuietly(project.id()), "scratch-project-cleanup"));
        return project.id();
    }
}
