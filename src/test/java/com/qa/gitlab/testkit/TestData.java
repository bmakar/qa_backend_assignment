package com.qa.gitlab.testkit;

import com.qa.gitlab.model.CreateIssueRequest;

import java.util.UUID;

/**
 * Factories for uniquely named test data.
 * <p>
 * Every created resource carries a unique suffix so that list and search assertions stay
 * deterministic when the suite runs in parallel, or when two engineers run it at the same time
 * against the same project.
 * <p>
 * The boundary helpers live here rather than inline in the tests so that the intent of each edge
 * case is named once - {@code emojiTitle()} says what it is, a literal string of surrogate pairs
 * does not.
 */
public final class TestData {

    private static final String PREFIX = "qa-";

    /**
     * An internal id that cannot exist in a freshly created project. Kept here rather than repeated
     * as a literal in every negative test.
     */
    public static final long UNKNOWN_IID = 999_999_999L;

    /** A project id well beyond anything gitlab.com has allocated. */
    public static final long UNKNOWN_PROJECT_ID = 999_999_999_999L;

    private TestData() {
    }

    public static String uniqueTitle() {
        return uniqueTitle("issue");
    }

    public static String uniqueTitle(String hint) {
        return PREFIX + hint + "-" + shortId();
    }

    public static String uniqueLabel() {
        return PREFIX + "label-" + shortId();
    }

    /** Smallest payload the API accepts: title only. */
    public static CreateIssueRequest minimalIssue() {
        return CreateIssueRequest.builder()
                .title(uniqueTitle())
                .build();
    }

    /** A title of exactly {@code length} characters, still unique so listings stay deterministic. */
    public static String titleOfLength(int length) {
        String unique = uniqueTitle();
        if (unique.length() >= length) {
            return unique.substring(0, length);
        }
        return unique + "x".repeat(length - unique.length());
    }

    /** Non-Latin scripts plus a right-to-left run, to catch naive byte-length validation. */
    public static String unicodeTitle() {
        return uniqueTitle("unicode") + " 日本語 Ελληνικά مرحبا";
    }

    /** Emoji are outside the basic multilingual plane, so each is a surrogate pair. */
    public static String emojiTitle() {
        return uniqueTitle("emoji") + " 🚀🔥✅";
    }

    /**
     * Markup that must be stored verbatim. The API is a data store, not a renderer - escaping
     * belongs to whatever displays the value, so a round-trip has to return exactly what was sent.
     */
    public static String markupTitle() {
        return uniqueTitle("markup") + " <script>alert('xss')</script> & **bold** | `code`";
    }

    /** SQL-shaped input, for the same round-trip-verbatim reason. */
    public static String sqlishTitle() {
        return uniqueTitle("sqlish") + " '; DROP TABLE issues; --";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
