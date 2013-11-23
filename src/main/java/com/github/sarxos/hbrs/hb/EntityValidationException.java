package com.github.sarxos.hbrs.hb;

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;


public class EntityValidationException extends ValidationException {

	private static final long serialVersionUID = 1L;

	private Set<ConstraintViolation<Object>> violations = null;
	private Object entity = null;

	public EntityValidationException(Object entity, Set<ConstraintViolation<Object>> violations) {
		this.entity = entity;
		this.violations = violations;
	}

	public Object getEntity() {
		return entity;
	}

	public Set<ConstraintViolation<Object>> getViolations() {
		return violations;
	}

}
