package com.qa.gitlab.tests;

import com.qa.gitlab.testkit.ApiTest;
import com.qa.gitlab.testkit.Schemas;
import com.qa.gitlab.testkit.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * The error-response schema, split from {@link IssueSchemaTest} so the success and error shapes
 * can each be read on their own.
 */
@DisplayName("Response schema contract - errors")
class IssueErrorSchemaTest extends ApiTest {

    @Test
    @DisplayName("Match the error schema on a 404")
    void notFoundErrorMatchesErrorSchema() {
        issues.getRaw(projectId(), TestData.UNKNOWN_IID)
                .then().body(Schemas.error());
    }

    @Test
    @DisplayName("Match the error schema on a 400 validation error")
    void validationErrorMatchesErrorSchema() {
        createRawTracked(Map.of("description", "no title supplied"))
                .then().body(Schemas.error());
    }
}
