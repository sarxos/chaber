package com.github.sarxos.hbrs.rs.mapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class ConstraintViolationExceptionMapper extends AbstractExceptionMapper<ConstraintViolationException> {

	@Override
	public Response toResponse(ConstraintViolationException e) {

		Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
		Map<String, List<String>> vmap = new HashMap<String, List<String>>();
		List<String> list = null;

		for (ConstraintViolation<?> v : violations) {

			String property = v.getPropertyPath().toString();
			Class<?> clazz = v.getLeafBean().getClass();

			String name = null;

			if (clazz == v.getRootBeanClass()) {
				int k = property.lastIndexOf(".");
				if (k != -1) {
					name = property.substring(0, k);
				} else {
					name = property;
				}
			} else {

				property = property.substring(property.lastIndexOf(".") + 1);

				Field field = null;
				try {
					field = clazz.getDeclaredField(property);
				} catch (NoSuchFieldException ee) {
					throw new RuntimeException(String.format("Class '%s' does not define field '%s'", clazz.getName(), property), ee);
				} catch (SecurityException ee) {
					throw new RuntimeException(ee);
				}

				JsonProperty jp = field.getAnnotation(JsonProperty.class);
				if (jp != null) {
					name = jp.value();
				}
				if (name == null) {
					name = field.getName();
				}
			}

			if ((list = vmap.get(name)) == null) {
				vmap.put(name, list = new ArrayList<String>());
			}

			list.add(v.getMessage());
		}

		Map<String, Object> r = new HashMap<String, Object>();
		r.put("message", "Bean validation failed");
		r.put("constraints", vmap);

		return Response
			.status(Status.BAD_REQUEST)
			.type(MediaType.APPLICATION_JSON)
			.entity(r)
			.build();
	}
}
