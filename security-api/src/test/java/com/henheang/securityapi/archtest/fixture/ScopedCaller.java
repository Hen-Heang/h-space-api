package com.henheang.securityapi.archtest.fixture;

import com.henheang.securityapi.archtest.fixture.repository.ScopedFixtureRepository;
import com.henheang.securityapi.domain.User;
import java.util.UUID;

/** Fixture: uses only scoped finders, plus save. */
public class ScopedCaller {

    public User load(ScopedFixtureRepository repository, UUID id, UUID userId) {
        User user = repository.findByIdAndUserId(id, userId).orElseThrow();
        return repository.save(user);
    }
}
