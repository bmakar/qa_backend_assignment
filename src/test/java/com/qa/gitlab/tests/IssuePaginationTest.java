package com.qa.gitlab.tests;

import com.qa.gitlab.client.Page;
import com.qa.gitlab.model.CreateIssueRequest;
import com.qa.gitlab.model.Issue;
import com.qa.gitlab.model.IssueQuery;
import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pagination on {@code GET /projects/:id/issues}.
 * <p>
 * Most of this contract lives in response <em>headers</em>, not in the body, which is why a suite
 * that only deserializes the JSON array tends to skip pagination entirely and never notices.
 * {@link Page} exists to make those headers assertable.
 * <p>
 * Every test scopes itself with a unique label so that {@code X-Total} is a number this test knows
 * the right answer to. Asserting totals against a shared project would otherwise be untestable.
 */
@DisplayName("GET /projects/:id/issues - pagination")
class IssuePaginationTest extends ApiTest {

    /** Not a multiple of the page sizes used below, so the last page is always partial. */
    private static final int SEEDED = 7;

    private static final int PAGE_SIZE = 3;

    @Test
    @DisplayName("Report X-Total, X-Total-Pages and X-Per-Page for a scoped collection")
    void reportsTotalsForAScopedCollection() {
        String marker = seed(SEEDED);

        Page<Issue> page = issues.listPage(projectId(), scoped(marker).perPage(PAGE_SIZE).build());

        assertThat(page.items()).hasSize(PAGE_SIZE);
        assertThat(page.total()).contains(SEEDED);
        // 7 items at 3 per page is 3 pages, the last one partial.
        assertThat(page.totalPages()).contains(3);
        assertThat(page.perPage()).contains(PAGE_SIZE);
        assertThat(page.page()).contains(1);
    }

    @Test
    @DisplayName("Omit X-Prev-Page on the first page")
    void omitsPreviousPageOnTheFirstPage() {
        String marker = seed(SEEDED);

        Page<Issue> first = issues.listPage(projectId(), scoped(marker).perPage(PAGE_SIZE).page(1).build());

        assertThat(first.prevPage()).isEmpty();
        assertThat(first.nextPage()).contains(2);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    @DisplayName("Omit X-Next-Page on the last page")
    void omitsNextPageOnTheLastPage() {
        String marker = seed(SEEDED);

        Page<Issue> last = issues.listPage(projectId(), scoped(marker).perPage(PAGE_SIZE).page(3).build());

        // 7 items, 3 per page: the third page holds the remaining one.
        assertThat(last.items()).hasSize(1);
        assertThat(last.nextPage()).isEmpty();
        assertThat(last.prevPage()).contains(2);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Offer navigable first/last/prev/next Link headers")
    void offersNavigableLinkHeaders() {
        String marker = seed(SEEDED);

        Page<Issue> middle = issues.listPage(projectId(), scoped(marker).perPage(PAGE_SIZE).page(2).build());

        assertThat(middle.links())
                .containsKeys("first", "last", "prev", "next");
        assertThat(middle.links().get("next")).contains("page=3");
        assertThat(middle.links().get("prev")).contains("page=1");
    }

    @Test
    @DisplayName("Apply the documented default page size of 20")
    void appliesTheDocumentedDefaultPageSize() {
        String marker = seed(SEEDED);

        Page<Issue> page = issues.listPage(projectId(), scoped(marker).build());

        // Asserting the server's default rather than our own: omitting per_page must yield 20.
        assertThat(page.perPage()).contains(IssueQuery.DEFAULT_PER_PAGE);
    }

    @Test
    @DisplayName("Clamp per_page above 100 rather than rejecting or honouring it")
    void clampsPageSizeAboveTheDocumentedMaximum() {
        seed(1);

        Page<Issue> page = issues.listPage(projectId(), IssueQuery.builder().perPage(101).build());

        // 101 must be clamped to 100, not rejected and not honoured.
        assertThat(page.perPage()).contains(IssueQuery.MAX_PER_PAGE);
    }

    @Test
    @DisplayName("Return an empty page beyond the last one, not a 404")
    void returnsAnEmptyPageBeyondTheLastOne() {
        String marker = seed(2);

        Page<Issue> page = issues.listPage(projectId(), scoped(marker).perPage(PAGE_SIZE).page(99).build());

        // Past the end is an empty array, not a 404.
        assertThat(page.items()).isEmpty();
        assertThat(page.nextPage()).isEmpty();
    }

    @ParameterizedTest(name = "Per_page={0} is rejected or coerced, never honoured verbatim")
    @ValueSource(ints = {0, -1})
    @DisplayName("Handle a non-positive per_page value")
    void handlesNonPositivePageSizes(int perPage) {
        seed(2);

        Response response = issues.listRaw(projectId(), IssueQuery.builder()
                .raw(Map.of("per_page", perPage))
                .build());

        // GitLab coerces these to its default rather than erroring. Either behaviour is defensible;
        // returning zero items would not be, because it would look like an empty project.
        if (response.statusCode() == 200) {
            assertThat(response.jsonPath().getList("")).isNotEmpty();
        } else {
            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    @ParameterizedTest(name = "Page={0} is coerced to the first page")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("Coerce a non-positive page number to the first page")
    void coercesNonPositivePageNumbers(String page) {
        String marker = seed(2);

        // per_page=1 over 2 seeded issues means there are two real pages, so landing on the first
        // one is an observable outcome rather than the only one available.
        Response response = issues.listRaw(projectId(), scoped(marker)
                .perPage(1)
                .raw(Map.of("page", page))
                .build());

        // FINDING-3: GitLab coerces a non-positive page to the first page and answers 200 rather
        // than rejecting it. Pinned as the actual behaviour: what matters is that an unusable value
        // lands on the first page rather than on some arbitrary other one.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getHeader("X-Page")).isEqualTo("1");
        assertThat(response.getHeader("X-Total")).isEqualTo("2");
        assertThat(response.jsonPath().getList("")).hasSize(1);
    }

    @Test
    @DisplayName("Handle a non-numeric page number")
    void handlesNonNumericPageNumber() {
        String marker = seed(2);

        Response response = issues.listRaw(projectId(), scoped(marker)
                .perPage(1)
                .raw(Map.of("page", "not-a-number"))
                .build());

        // Kept separate from the non-positive values above: a value that is not an integer at all
        // may legitimately be rejected outright, where 0 and -1 are coerced. Either is defensible;
        // a 5xx would not be.
        assertThat(response.statusCode()).isIn(200, 400);
        if (response.statusCode() == 200) {
            // per_page is still honoured, so this is a real first page and not an unbounded dump.
            // X-Page is deliberately not asserted: whether GitLab echoes a page number for a
            // non-integer value was never observed, and pinning an unobserved value is how a test
            // starts asserting fiction.
            assertThat(response.jsonPath().getList("")).hasSize(1);
        }
    }

    @Test
    @DisplayName("Support keyset pagination via pagination=keyset")
    void supportsKeysetPagination() {
        String marker = seed(SEEDED);

        Response response = issues.listRaw(projectId(), scoped(marker)
                .perPage(PAGE_SIZE)
                .pagination("keyset")
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        Page<Issue> page = Page.from(response, Issue[].class);
        assertThat(page.items()).hasSize(PAGE_SIZE);
        // Keyset pagination navigates by Link header only - there is no page number to increment.
        assertThat(page.links()).containsKey("next");
        assertThat(page.links().get("next")).contains("cursor");
    }

    /** Creates {@code count} issues sharing a fresh label, and returns that label. */
    private String seed(int count) {
        String marker = TestData.uniqueLabel();
        for (int i = 0; i < count; i++) {
            givenIssue(CreateIssueRequest.builder()
                    .title(TestData.uniqueTitle("page-" + i))
                    .labels(marker)
                    .build());
        }
        return marker;
    }

    private static IssueQuery.IssueQueryBuilder scoped(String marker) {
        return IssueQuery.builder().labels(List.of(marker));
    }
}
