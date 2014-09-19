package com.github.sarxos.hbrs.rs.mapper;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


/**
 * This exception is something which we do not expect, but it'snot critical.
 * 
 * @author Bartosz Firyn (sarxos)
 */
@Provider
public class HibernateConstraintViolationExceptionMapper extends AbstractExceptionMapper<ConstraintViolationException> {

	private static final Logger LOG = LoggerFactory.getLogger(HibernateConstraintViolationExceptionMapper.class);

	@Override
	public Response toResponse(ConstraintViolationException exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		LOG.info(exception.getMessage(), exception);

		Map<String, String> r = new HashMap<String, String>();
		r.put("exception", exception.getClass().getName());
		r.put("message", exception.getMessage());

		return Response
			.status(Status.BAD_REQUEST)
			.type(MediaType.APPLICATION_JSON)
			.entity(r)
			.build();
	}
}
