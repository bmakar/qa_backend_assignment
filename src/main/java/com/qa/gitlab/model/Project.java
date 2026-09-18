package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Minimal project projection - only what the scratch-project lifecycle needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(

        long id,

        String name,

        String path,

        String pathWithNamespace,

        String webUrl) {
}
