package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

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

	@Override
	public Response toResponse(NotFoundException exception) {
		return build(exception, Status.NOT_FOUND);
	}
}
