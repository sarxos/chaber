package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

	private static final Logger LOG = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class);

	private ObjectMapper mapper = new ObjectMapper();

	@Override
	public Response toResponse(IllegalArgumentException exception) {
		try {
			return Response
				.status(Status.BAD_REQUEST)
				.entity(mapper.writeValueAsString(exception.getMessage()))
				.build();
		} catch (JsonProcessingException e) {
			LOG.error(e.getMessage(), e);
			return Response
				.status(Status.BAD_REQUEST)
				.entity("mapper fallback: " + exception.getMessage())
				.build();
		}
	}
}
