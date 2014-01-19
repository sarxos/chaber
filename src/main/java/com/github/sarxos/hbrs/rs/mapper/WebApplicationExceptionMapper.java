package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class WebApplicationExceptionMapper extends AbstractExceptionMapper<WebApplicationException> {

	@Override
	public Response toResponse(WebApplicationException exception) {
		int code = exception.getResponse().getStatus();
		Response.Status status = Response.Status.fromStatusCode(code);
		return build(exception, status);
	}
}
