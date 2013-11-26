package com.github.sarxos.hbrs.rs.mapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.sarxos.hbrs.hb.EntityValidationException;


@Provider
public class EntityValidationExceptionMapper implements ExceptionMapper<EntityValidationException> {

	@Override
	public Response toResponse(EntityValidationException exception) {

		Set<ConstraintViolation<Object>> violations = exception.getViolations();
		Class<?> clazz = exception.getEntity().getClass();

		Map<String, List<String>> vmap = new HashMap<String, List<String>>();

		List<String> list = null;

		for (ConstraintViolation<Object> violation : violations) {

			String property = violation.getPropertyPath().toString();

			Field field = null;
			try {
				field = clazz.getDeclaredField(property);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(String.format("Class '%s' does not define field '%s'", clazz.getName(), property), e);
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}

			String name = null;

			JsonProperty jp = field.getAnnotation(JsonProperty.class);
			if (jp != null) {
				name = jp.value();
			}
			if (name == null) {
				name = field.getName();
			}

			if ((list = vmap.get(name)) == null) {
				vmap.put(name, list = new ArrayList<String>());
			}

			list.add(violation.getMessage());
		}

		Map<String, Object> r = new HashMap<String, Object>();
		r.put("message", String.format("%s entity validation failed", clazz.getSimpleName()));
		r.put("fields", vmap);

		return Response
			.status(Status.BAD_REQUEST)
			.entity(r)
			.build();
	}
}
