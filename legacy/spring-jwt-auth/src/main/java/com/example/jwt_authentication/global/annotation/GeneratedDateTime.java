package com.example.jwt_authentication.global.annotation;

import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.generator.EventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ValueGenerationType( generatedBy = DateTimeGeneration.class)
@Retention(RetentionPolicy.RUNTIME)
@Target( { ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE } )
public @interface GeneratedDateTime {
     EventType[] timing();

     String pattern() default "yyyyMMddHHmmss";
}
