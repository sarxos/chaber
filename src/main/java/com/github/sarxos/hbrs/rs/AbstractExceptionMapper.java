package com.github.sarxos.hbrs.rs;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;


@Singleton
@Priority(Priorities.USER)
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

	protected Response build(T exception) {
		return build(exception, Status.INTERNAL_SERVER_ERROR);
	}

	protected Response build(T exception, Status status) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		Map<String, String> r = new HashMap<String, String>();
		r.put("exception", exception.getClass().getName());
		r.put("message", exception.getMessage());

		return Response
			.status(status)
			.entity(r)
			.build();
	}
}
