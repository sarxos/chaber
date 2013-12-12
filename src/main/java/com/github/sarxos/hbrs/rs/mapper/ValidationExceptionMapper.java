package com.github.sarxos.hbrs.rs.mapper;

import javax.validation.ValidationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class ValidationExceptionMapper extends AbstractExceptionMapper<ValidationException> {

	private static final Logger LOG = LoggerFactory.getLogger(ValidationExceptionMapper.class);

	@Override
	public Response toResponse(ValidationException e) {
		LOG.error(e.getMessage(), e);
		return build(e, Status.INTERNAL_SERVER_ERROR);
	}
}
