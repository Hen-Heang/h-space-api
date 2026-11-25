package com.henheang.apicore.archtest.fixture;

import java.util.UUID;

/**
 * Fixture: the row type of the fixture repositories. Only ArchUnit reads it, so it isn't a JPA
 * entity and never needs a table.
 */
public class FixtureRow {

    private UUID id;

    public UUID getId() {
        return id;
    }
}
