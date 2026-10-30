package com.ponomarenko.mugloar.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/**
 * A game, ad or item identifier as the game API issues them: a short alphanumeric token.
 * Anything else is rejected before it is ever forwarded upstream.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = ValidId.PATTERN)
@ReportAsSingleViolation
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidId {

	String PATTERN = "^[A-Za-z0-9_-]{1,64}$";

	String message() default "must be 1-64 characters of letters, digits, '-' or '_'";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
