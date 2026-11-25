package com.henheang.apicore.archtest.fixture;

import com.henheang.apicore.archtest.fixture.repository.ScopedFixtureRepository;
import java.util.UUID;

/** Fixture: uses only scoped finders, plus save. */
public class ScopedCaller {

    public FixtureRow load(ScopedFixtureRepository repository, UUID id, UUID userId) {
        FixtureRow user = repository.findByIdAndUserId(id, userId).orElseThrow();
        return repository.save(user);
    }
}
