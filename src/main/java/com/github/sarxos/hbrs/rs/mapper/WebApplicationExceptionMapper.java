package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

	private static final Logger LOG = LoggerFactory.getLogger(WebApplicationExceptionMapper.class);

	private ObjectMapper mapper = new ObjectMapper();

	@Override
	public Response toResponse(WebApplicationException exception) {
		try {
			return Response
				.status(exception.getResponse().getStatus())
				.entity(mapper.writeValueAsString(exception.getMessage()))
				.build();
		} catch (JsonProcessingException e) {
			LOG.error(e.getMessage(), e);
			return Response
				.status(exception.getResponse().getStatus())
				.entity("mapper fallback: " + exception.getMessage())
				.build();
		}
	}
}
