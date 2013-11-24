package com.github.sarxos.hbrs.rs.mapper;

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

import com.github.sarxos.hbrs.hb.EntityValidationException;


@Provider
public class EntityValidationExceptionMapper implements ExceptionMapper<EntityValidationException> {

	@Override
	public Response toResponse(EntityValidationException exception) {

		Set<ConstraintViolation<Object>> violations = exception.getViolations();

		Map<String, List<String>> vmap = new HashMap<String, List<String>>();

		for (ConstraintViolation<Object> violation : violations) {

			String property = violation.getPropertyPath().toString();
			List<String> list = vmap.get(property);

			if (list == null) {
				vmap.put(property, list = new ArrayList<String>());
			}

			list.add(violation.getMessage());
		}

		Map<String, Object> r = new HashMap<String, Object>();
		r.put("message", String.format("%s entity validation failed", exception.getEntity().getClass().getSimpleName()));
		r.put("fields", vmap);

		return Response
			.status(Status.BAD_REQUEST)
			.entity(r)
			.build();
	}
}
