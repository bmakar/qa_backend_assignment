package com.qa.gitlab.model;

import lombok.Builder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters, ordering and pagination for {@code GET /projects/:id/issues}.
 * <p>
 * Query parameters are assembled explicitly rather than reflectively, so every wire name is visible
 * and greppable in {@link #asQueryParams()} instead of being derived from a field name at runtime.
 * Values are typed wherever the API has a closed set - {@link IssueStateFilter},
 * {@link IssueOrderBy}, {@link DueDateFilter} - because a mistyped {@code "opend"} in a raw string
 * silently degrades a filter test into an unfiltered one that still passes.
 * <p>
 * {@link #raw} is the escape hatch: deliberately invalid values belong there, so the typed surface
 * never has to accept garbage in order to let negative tests exist.
 */
@Builder
public record IssueQuery(

        IssueStateFilter state,

        /** Concrete label set; mutually exclusive with {@link #labelsPresence}. */
        List<String> labels,

        /** {@code Any} or {@code None}; mutually exclusive with {@link #labels}. */
        FilterValue labelsPresence,

        Boolean withLabelsDetails,

        String search,

        List<SearchIn> in,

        FilterValue assigneeId,

        String assigneeUsername,

        Long authorId,

        String authorUsername,

        FilterValue milestone,

        IssueScope scope,

        IssueType issueType,

        Boolean confidential,

        List<Long> iids,

        DueDateFilter dueDate,

        Instant createdAfter,

        Instant createdBefore,

        Instant updatedAfter,

        Instant updatedBefore,

        Boolean nonArchived,

        IssueOrderBy orderBy,

        SortDirection sort,

        /** Negated filters, rendered as {@code not[<key>]=<value>}. */
        Map<String, Object> not,

        Integer perPage,

        Integer page,

        /** {@code keyset} selects keyset pagination instead of the default offset pagination. */
        String pagination,

        /** Verbatim parameters, for values the typed surface intentionally cannot express. */
        Map<String, Object> raw) {

    public static IssueQuery none() {
        return IssueQuery.builder().build();
    }

    /** Convenience for the common "give me everything on one page" case in listing assertions. */
    public static IssueQuery allStatesMaxPage() {
        return IssueQuery.builder()
                .state(IssueStateFilter.ALL)
                .perPage(MAX_PER_PAGE)
                .build();
    }

    /** GitLab clamps {@code per_page} at 100. */
    public static final int MAX_PER_PAGE = 100;

    /** GitLab's default page size when {@code per_page} is omitted. */
    public static final int DEFAULT_PER_PAGE = 20;

    public Map<String, Object> asQueryParams() {
        if (labels != null && labelsPresence != null) {
            throw new IllegalStateException(
                    "Set either labels or labelsPresence - GitLab accepts one 'labels' parameter, not both");
        }

        Map<String, Object> params = new LinkedHashMap<>();

        put(params, "state", state == null ? null : state.wireValue());
        put(params, "labels", labels != null ? String.join(",", labels) : wire(labelsPresence));
        put(params, "with_labels_details", withLabelsDetails);
        put(params, "search", search);
        put(params, "in", in == null ? null : String.join(",", in.stream().map(SearchIn::wireValue).toList()));
        put(params, "assignee_id", wire(assigneeId));
        put(params, "assignee_username", assigneeUsername);
        put(params, "author_id", authorId);
        put(params, "author_username", authorUsername);
        put(params, "milestone", wire(milestone));
        put(params, "scope", scope == null ? null : scope.wireValue());
        put(params, "issue_type", issueType == null ? null : issueType.wireValue());
        put(params, "confidential", confidential);
        // Repeated array syntax, which is what GitLab documents for iids.
        put(params, "iids[]", iids);
        put(params, "due_date", dueDate == null ? null : dueDate.wireValue());
        put(params, "created_after", createdAfter);
        put(params, "created_before", createdBefore);
        put(params, "updated_after", updatedAfter);
        put(params, "updated_before", updatedBefore);
        put(params, "non_archived", nonArchived);
        put(params, "order_by", orderBy == null ? null : orderBy.wireValue());
        put(params, "sort", sort == null ? null : sort.wireValue());
        put(params, "per_page", perPage);
        put(params, "page", page);
        put(params, "pagination", pagination);

        if (not != null) {
            not.forEach((key, value) -> put(params, "not[" + key + "]", value));
        }
        if (raw != null) {
            raw.forEach((key, value) -> put(params, key, value));
        }
        return params;
    }

    private static String wire(FilterValue value) {
        return value == null ? null : value.wireValue();
    }

    private static void put(Map<String, Object> params, String name, Object value) {
        if (value != null) {
            params.put(name, value instanceof Instant instant ? instant.toString() : value);
        }
    }
}
