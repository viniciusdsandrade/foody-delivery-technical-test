package com.foody.tracker.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates the UTF-8 byte length of a String. Needed where the storage or
 * algorithm limit is in bytes, not characters (e.g. BCrypt's 72-byte input).
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

    String message() default "must not exceed {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value();
}
