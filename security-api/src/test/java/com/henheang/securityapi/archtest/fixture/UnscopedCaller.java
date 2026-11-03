package com.henheang.securityapi.archtest.fixture;

import com.henheang.securityapi.archtest.fixture.repository.ScopedFixtureRepository;
import java.util.UUID;
import java.util.function.Supplier;

/** Fixture: reaches past the owner, with a direct call and with a method reference. */
public class UnscopedCaller {

    public boolean probe(ScopedFixtureRepository repository, UUID id) {
        Supplier<Long> counter = repository::count;
        return repository.findById(id).isPresent() && counter.get() > 0;
    }
}
