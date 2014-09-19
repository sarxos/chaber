package com.github.sarxos.hbrs.rs.mapper;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ParamException;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class ParamExceptionMapper extends AbstractExceptionMapper<ParamException> {

	@Override
	public Response toResponse(ParamException exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		String pname = exception.getParameterName();
		Class<?> clazz = exception.getParameterType();
		String message = String.format("Invalid parameter %s sent in the request for %s", pname, clazz);

		Map<String, String> r = new HashMap<String, String>();
		r.put("exception", exception.getClass().getName());
		r.put("message", message);

		return Response
			.status(Status.BAD_REQUEST)
			.entity(r)
			.build();
	}
}
