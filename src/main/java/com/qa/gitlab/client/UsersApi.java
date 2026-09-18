package com.qa.gitlab.client;

import com.qa.gitlab.model.Author;
import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * The authenticated user.
 * <p>
 * Exists so a test that needs "my own user id" - to build an assignee filter, for instance - has a
 * one-call way to get it. The alternative seen elsewhere in earlier versions of this suite was
 * {@code givenIssue().author().id()}: create a disposable issue for no reason but to read its
 * author back, then delete it in teardown. That is two HTTP round trips and a throwaway row in a
 * shared project to answer a question this single endpoint answers directly.
 */
public final class UsersApi {

    private final Supplier<RequestSpecification> spec;

    public UsersApi() {
        this(GitLabClient::spec);
    }

    public UsersApi(Supplier<RequestSpecification> spec) {
        this.spec = spec;
    }

    /** The user the configured token authenticates as. */
    public Author current() {
        return spec.get()
                .when().get("/user")
                .then().statusCode(200)
                .extract().as(Author.class);
    }
}
