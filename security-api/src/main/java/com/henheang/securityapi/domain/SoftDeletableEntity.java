package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

// Entities where "deleted" must mean hidden, not gone - losing history (who
// had what role, which permissions existed) would itself be a security
// regression. Concrete subclasses pair this with @SQLDelete + @SQLRestriction
// so delete()/normal queries do the right thing transparently.
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
