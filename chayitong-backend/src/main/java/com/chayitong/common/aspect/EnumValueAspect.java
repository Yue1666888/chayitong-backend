package com.chayitong.common.aspect;

import com.chayitong.common.annotation.EnumValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EnumValueAspect implements ConstraintValidator<EnumValue, Object> {

	private EnumValue annotation;

	@Override
	public void initialize(EnumValue annotation) {
		this.annotation = annotation;
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		String candidate = value.toString();
		for (Enum<?> constant : annotation.value().getEnumConstants()) {
			if (annotation.ignoreCase() ? constant.name().equalsIgnoreCase(candidate) : constant.name().equals(candidate)) {
				return true;
			}
		}
		return false;
	}
}
