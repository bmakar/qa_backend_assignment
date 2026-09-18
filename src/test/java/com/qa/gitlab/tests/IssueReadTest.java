package com.qa.gitlab.tests;

import com.qa.gitlab.model.Author;
import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueType;
import com.qa.gitlab.model.Severity;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrieving a single issue. Listing and its filters live in {@code IssueListFilterTest}, and
 * pagination in {@code IssuePaginationTest} - one endpoint per class keeps each one readable.
 * Not-found and cross-project cases live in {@link IssueReadNotFoundTest}.
 */
@DisplayName("GET /projects/:id/issues/:iid")
class IssueReadTest extends ApiTest {

    @Test
    @DisplayName("Retrieve an issue by its internal id with every field matching the create response")
    void getsIssueByInternalId() {
        Issue created = givenIssue();

        Issue fetched = issues.get(projectId(), created.iid());

        // One assertion covering every field, with a readable diff on failure.
        // updatedAt is excluded because background processing can touch it after creation.
        assertThat(fetched)
                .usingRecursiveComparison()
                .ignoringFields("updatedAt")
                .isEqualTo(created);
    }

    @Test
    @DisplayName("Resolve the same issue by URL-encoded project path as by numeric id")
    void resolvesSameIssueByUrlEncodedProjectPath() {
        Issue created = givenIssue();

        // The API documents :id as "integer or string - the global ID or URL-encoded path", so this
        // is the second half of a documented addressing contract that the rest of the suite never
        // exercises. Both spellings must name the same resource.
        Issue byPath = issues.getRaw(projectPath(), created.iid())
                .then().statusCode(200)
                .extract().as(Issue.class);

        assertThat(byPath.id()).isEqualTo(created.id());
        assertThat(byPath.iid()).isEqualTo(created.iid());
        assertThat(byPath.projectId()).isEqualTo(projectId());
    }

    @Test
    @DisplayName("Return every field that was set on creation")
    void returnsEveryFieldThatWasSetOnCreation() {
        // The same payload IssueCreateTest.createsIssueWithFullPayload sends, asserted through a
        // fresh GET rather than off the create response - so a value that is stored correctly but
        // mangled, dropped or defaulted on the way back out is caught here and nowhere else. The
        // fields left out are the ones that cannot be satisfied at all: milestoneId / milestone need
        // an existing milestone, weight and epic_id need Premium/Ultimate.
        LocalDate due = LocalDate.now().plusDays(3);
        LocalDate start = LocalDate.now().plusDays(1);
        String label = TestData.uniqueLabel();
        long selfId = selfId();
        Instant backdated = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long requestedIid = highestExistingIid() + 50;

        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("read-back"))
                .description("read-back check")
                .labels(label)
                .confidential(true)
                .assigneeIds(List.of(selfId))
                .dueDate(due)
                .startDate(start)
                .issueType(IssueType.INCIDENT)
                .severity(Severity.HIGH)
                .createdAt(backdated)
                .iid(requestedIid)
                .build());

        Issue fetched = issues.get(projectId(), created.iid());

        assertThat(fetched.title()).isEqualTo(created.title());
        assertThat(fetched.description()).isEqualTo("read-back check");
        assertThat(fetched.labels()).containsExactly(label);
        assertThat(fetched.confidential()).isTrue();
        assertThat(fetched.assignees()).extracting(Author::id).containsExactly(selfId);
        assertThat(fetched.dueDate()).isEqualTo(due);
        assertThat(fetched.startDate()).isEqualTo(start);
        assertThat(fetched.issueType()).isEqualTo(IssueType.INCIDENT);
        assertThat(fetched.severity()).isEqualTo(Severity.HIGH);
        assertThat(fetched.createdAt())
                .as("a backdated created_at must survive the round trip, not be re-stamped on read")
                .isEqualTo(backdated);
        assertThat(fetched.iid()).isEqualTo(requestedIid);
        // FINDING-4: GitLab now addresses an issue as /-/work_items/:iid, not /-/issues/:iid. Both
        // name the same resource, so what is pinned is that web_url is an absolute URL addressing
        // this issue by its iid - not which path segment GitLab happens to use for it.
        assertThat(fetched.webUrl())
                .startsWith("https://")
                .endsWith("/" + created.iid())
                .containsAnyOf("/issues/", "/work_items/");
    }
}
