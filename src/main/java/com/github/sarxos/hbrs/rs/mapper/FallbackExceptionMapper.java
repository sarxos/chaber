package com.github.sarxos.hbrs.rs.mapper;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is fallback exception mapper which simply means that it will be used
 * when there was no other appropriate mapper found to map the exception to
 * specific response. This mapper will always return HTTP 500.
 * 
 * @author Bartosz Firyn (sarxos)
 */
@Provider
public class FallbackExceptionMapper implements ExceptionMapper<Throwable> {

	/**
	 * Yup, this is a logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(FallbackExceptionMapper.class);

	@Override
	public Response toResponse(Throwable exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		LOG.error(exception.getMessage(), exception);

		Throwable b = null;
		Throwable t = exception;
		do {
			b = t;
			t = t.getCause();
		} while (t != null);

		Map<String, String> r = new HashMap<String, String>();
		r.put("exception", b.getClass().getName());
		r.put("message", b.getMessage());

		return Response
			.status(Status.INTERNAL_SERVER_ERROR)
			.entity(r)
			.build();
	}
}
