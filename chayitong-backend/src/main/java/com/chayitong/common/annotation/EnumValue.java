package com.chayitong.common.annotation;

import com.chayitong.common.aspect.EnumValueAspect;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValueAspect.class)
public @interface EnumValue {

	Class<? extends Enum<?>> value();

	boolean ignoreCase() default false;

	String message() default "枚举值不合法";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
