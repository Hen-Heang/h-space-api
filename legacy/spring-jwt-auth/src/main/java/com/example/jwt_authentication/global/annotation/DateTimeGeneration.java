package com.example.jwt_authentication.global.annotation;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

/**
 *  This class is used to generate date time for entity
 *  that extends {@link BeforeExecutionGenerator}
 */
public class DateTimeGeneration implements BeforeExecutionGenerator {
    private final String pattern;
    private final EventType[] eventTypes;

    public DateTimeGeneration(GeneratedDateTime annotation) {
        eventTypes = annotation.timing();
        this.pattern = annotation.pattern();
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.copyOf(List.of(eventTypes));
    }
}
