package com.qa.gitlab.testkit;

import io.restassured.module.jsv.JsonSchemaValidator;
import org.hamcrest.Matcher;

/**
 * JSON Schema matchers for the response contract.
 * <p>
 * These complement the typed models rather than duplicating them. Deserialization into a record
 * proves only that the fields the model knows about could be read; because
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} is off and Jackson happily leaves a missing field null, a
 * response that dropped {@code web_url} or turned {@code iid} into a string would still map cleanly
 * and every existing assertion would still pass. Schema validation is what turns that silent
 * degradation into a failure.
 */
public final class Schemas {

    private Schemas() {
    }

    /** A single issue object. */
    public static Matcher<?> issue() {
        return JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/issue.json");
    }

    /** An array of issues - also pins that the list endpoint never returns a bare object. */
    public static Matcher<?> issueList() {
        return JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/issue-list.json");
    }

    /** An error body, under either the {@code message} or the {@code error} key. */
    public static Matcher<?> error() {
        return JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/error.json");
    }
}
