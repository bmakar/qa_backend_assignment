package com.qa.gitlab.tests;

import com.qa.gitlab.model.Author;
import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueState;
import com.qa.gitlab.model.IssueType;
import com.qa.gitlab.model.Severity;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("POST /projects/:id/issues")
class IssueCreateTest extends ApiTest {

    @Test
    @DisplayName("Create an issue with only the mandatory title")
    void createsIssueWithTitleOnly() {
        String title = TestData.uniqueTitle();

        Issue created = givenIssue(CreateIssueRequest.builder().title(title).build());

        assertThat(created.title()).isEqualTo(title);
        assertThat(created.state()).isEqualTo(IssueState.OPENED);
        assertThat(created.projectId()).isEqualTo(projectId());
        assertThat(created.iid()).isPositive();
        assertThat(created.id()).isPositive();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.closedAt()).isNull();
        assertThat(created.author()).isNotNull();
    }

    @Test
    @DisplayName("Create an issue with every optional field populated")
    void createsIssueWithFullPayload() {
        // issue_type is incident so that severity is meaningful rather than inert, and createdAt /
        // iid are set because the README requires a project you own, which is the right those two
        // need. The only fields left out are the ones that cannot be satisfied here at all:
        // milestoneId / milestone need an existing milestone (a different resource), weight and
        // epic_id need Premium/Ultimate, and the two merge-request resolution fields need an MR with
        // an open discussion. See COVERAGE.md.
        long selfId = selfId();
        Instant backdated = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        // Above the next value auto-assignment would pick, so a honoured iid is distinguishable from
        // one that would have been assigned anyway. The margin absorbs issues the other classes
        // create concurrently between this read and the create below.
        long requestedIid = highestExistingIid() + 50;

        CreateIssueRequest request = CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("Created by the CRUD suite.")
                .labels("bug,qa-automation")
                .confidential(true)
                .assigneeIds(List.of(selfId))
                .dueDate(LocalDate.now().plusDays(7))
                .startDate(LocalDate.now().plusDays(1))
                .issueType(IssueType.INCIDENT)
                .severity(Severity.HIGH)
                .createdAt(backdated)
                .iid(requestedIid)
                .build();

        Issue created = givenIssue(request);

        assertThat(created.title()).isEqualTo(request.title());
        assertThat(created.description()).isEqualTo(request.description());
        assertThat(created.confidential()).isTrue();
        assertThat(created.dueDate()).isEqualTo(request.dueDate());
        assertThat(created.startDate()).isEqualTo(request.startDate());
        assertThat(created.issueType()).isEqualTo(IssueType.INCIDENT);
        assertThat(created.severity()).isEqualTo(Severity.HIGH);
        assertThat(created.assignees()).extracting(Author::id).containsExactly(selfId);
        // The API does not guarantee label ordering. request.labels() is the comma-separated wire
        // form; split it back out rather than duplicating the literal here.
        assertThat(created.labels()).containsExactlyInAnyOrder(request.labels().split(","));
        assertThat(created.createdAt())
                .as("created_at is honoured for a project owner; the current time here means the "
                        + "token lacks those rights and the value was silently dropped")
                .isEqualTo(backdated);
        assertThat(created.iid())
                .as("an explicit iid is honoured for a project owner; a lower value here means it "
                        + "was dropped and auto-assignment took over")
                .isEqualTo(requestedIid);
    }

    @Test
    @DisplayName("Create an issue without optional fields and leave them at their server default")
    void createsIssueWithoutOptionalFields() {
        Issue created = givenIssue();

        assertThat(created.labels()).isEmpty();
        assertThat(created.confidential()).isFalse();
        assertThat(created.dueDate()).isNull();
        assertThat(created.assignees()).isEmpty();
        // A field absent from the request must not be invented by the server.
        assertThat(created.description()).isNull();
    }

    @Test
    @DisplayName("Default issue_type to issue when not specified")
    void defaultsIssueTypeToIssue() {
        Issue created = givenIssue();

        assertThat(created.issueType()).isEqualTo(IssueType.ISSUE);
    }


    @Test
    @DisplayName("Create an issue already assigned to a user")
    void assignsIssueToTheRequestedUser() {
        long selfId = selfId();

        Issue assigned = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle("assigned"))
                .assigneeIds(List.of(selfId))
                .build());

        assertThat(assigned.assignees())
                .extracting(a -> a.id())
                .containsExactly(selfId);
    }

    @Test
    @DisplayName("Assign iid sequentially per project while id stays globally unique")
    void assignsSequentialInternalIdsWithinProject() {
        Issue first = givenIssue();
        Issue second = givenIssue();

        // iid is per-project and monotonically increasing; id is global.
        assertThat(second.iid()).isGreaterThan(first.iid());
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("Make a created issue immediately readable (read-your-writes)")
    void createdIssueIsImmediatelyReadable() {
        Issue created = givenIssue();

        // Read-your-writes: a create that is not yet visible to a GET would make every
        // create-then-verify test in the suite intermittently wrong.
        assertThat(issues.get(projectId(), created.iid()).id()).isEqualTo(created.id());
    }
}
