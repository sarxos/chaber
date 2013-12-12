package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


/**
 * This is fallback exception mapper which simply means that it will be used
 * when there was no other appropriate mapper found to map the exception to
 * specific response. This mapper will always return HTTP 500.
 * 
 * @author Bartosz Firyn (sarxos)
 */
@Provider
public class NotFoundExceptionMapper extends AbstractExceptionMapper<NotFoundException> {

	/**
	 * Yup, this is a logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(NotFoundExceptionMapper.class);

	@Override
	public Response toResponse(NotFoundException exception) {
		return build(exception, Status.NOT_FOUND);
	}
}
