package com.qa.gitlab.tests;

import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueState;
import com.qa.gitlab.model.IssueType;
import com.qa.gitlab.model.StateEvent;
import com.qa.gitlab.model.UpdateIssueRequest;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error cases for this endpoint live in {@link IssueUpdateNegativeTest}.
 */
@DisplayName("PUT /projects/:id/issues/:iid")
class IssueUpdateTest extends ApiTest {

    @Test
    @DisplayName("Update the title and leave every other field untouched")
    void updatesTitleAndLeavesOtherFieldsUntouched() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .description("original description")
                .build());
        String newTitle = TestData.uniqueTitle("renamed");

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .title(newTitle)
                .build());

        assertThat(updated.title()).isEqualTo(newTitle);
        assertThat(updated.description()).isEqualTo(created.description());
        assertThat(updated.iid()).isEqualTo(created.iid());
        assertThat(updated.state()).isEqualTo(IssueState.OPENED);
    }

    @Test
    @DisplayName("Update the description")
    void updatesDescription() {
        Issue created = givenIssue();

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .description("rewritten by the update test")
                .build());

        assertThat(updated.description()).isEqualTo("rewritten by the update test");
    }

    @Test
    @DisplayName("Persist an update so a subsequent read reflects it")
    void persistsUpdateForSubsequentReads() {
        Issue created = givenIssue();
        String newTitle = TestData.uniqueTitle("persisted");

        issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .title(newTitle)
                .build());

        // Asserting only on the PUT response would not prove the change was stored.
        assertThat(issues.get(projectId(), created.iid()).title()).isEqualTo(newTitle);
    }

    @Test
    @DisplayName("Advance updated_at on a change while created_at never moves")
    void advancesUpdatedTimestamp() {
        Issue created = givenIssue();

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .title(TestData.uniqueTitle("touched"))
                .build());

        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        // The creation timestamp must never move.
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    @DisplayName("Add and remove individual labels without replacing the whole set")
    void addsAndRemovesLabels() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels("keep-me,drop-me")
                .build());

        Issue afterAdd = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .addLabels("added-label")
                .build());
        assertThat(afterAdd.labels()).contains("keep-me", "drop-me", "added-label");

        Issue afterRemove = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .removeLabels("drop-me")
                .build());
        assertThat(afterRemove.labels())
                .contains("keep-me", "added-label")
                .doesNotContain("drop-me");
    }

    @Test
    @DisplayName("Replace the whole label set")
    void replacesTheWholeLabelSet() {
        Issue created = givenIssue(CreateIssueRequest.builder()
                .title(TestData.uniqueTitle())
                .labels("first,second")
                .build());

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .labels("only-this-one")
                .build());

        assertThat(updated.labels()).containsExactly("only-this-one");
    }

    @Test
    @DisplayName("Toggle confidentiality on and off")
    void togglesConfidentiality() {
        Issue created = givenIssue();

        Issue hidden = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .confidential(true)
                .build());
        assertThat(hidden.confidential()).isTrue();

        Issue revealed = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .confidential(false)
                .build());
        assertThat(revealed.confidential()).isFalse();
    }

    @Test
    @DisplayName("Set the due date")
    void setsDueDate() {
        Issue created = givenIssue();
        LocalDate due = LocalDate.now().plusDays(10);

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .dueDate(due)
                .build());

        assertThat(updated.dueDate()).isEqualTo(due);
    }

    @Test
    @DisplayName("Change issue_type on an existing issue")
    void changesIssueType() {
        Issue created = givenIssue();

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .issueType(IssueType.INCIDENT)
                .build());

        assertThat(updated.issueType()).isEqualTo(IssueType.INCIDENT);
    }

    @Test
    @DisplayName("Close an issue through state_event")
    void closesIssueThroughStateEvent() {
        Issue created = givenIssue();

        Issue closed = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        assertThat(closed.state()).isEqualTo(IssueState.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    @DisplayName("Reopen a closed issue")
    void reopensClosedIssue() {
        Issue created = givenIssue();
        issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        Issue reopened = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.REOPEN)
                .build());

        assertThat(reopened.state()).isEqualTo(IssueState.OPENED);
        assertThat(reopened.closedAt()).isNull();
    }

    @Test
    @DisplayName("Leave a closed issue retrievable rather than deleting it")
    void closingDoesNotDeleteTheIssue() {
        Issue created = givenIssue();

        issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .stateEvent(StateEvent.CLOSE)
                .build());

        // Closing is an update, not a delete - the issue must remain retrievable.
        assertThat(issues.getRaw(projectId(), created.iid()).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Update several fields in a single request")
    void updatesSeveralFieldsInOneRequest() {
        Issue created = givenIssue();
        String newTitle = TestData.uniqueTitle("multi");
        LocalDate due = LocalDate.now().plusDays(2);

        Issue updated = issues.update(projectId(), created.iid(), UpdateIssueRequest.builder()
                .title(newTitle)
                .description("multi-field update")
                .labels("multi")
                .confidential(true)
                .dueDate(due)
                .build());

        assertThat(updated.title()).isEqualTo(newTitle);
        assertThat(updated.description()).isEqualTo("multi-field update");
        assertThat(updated.labels()).containsExactly("multi");
        assertThat(updated.confidential()).isTrue();
        assertThat(updated.dueDate()).isEqualTo(due);
    }
}
