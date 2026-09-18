package com.qa.gitlab.testkit;

/**
 * Tag names, as constants so a typo cannot silently exclude a whole class from a run.
 * <p>
 * A mistyped tag in {@code -Dgroups} is the classic way a suite reports success having executed
 * nothing; the pom pairs this with {@code failIfNoSpecifiedTests} so that outcome fails instead.
 */
public final class Tags {

    /** Talks to a live GitLab instance. */
    public static final String API = "api";

    private Tags() {
    }
}
