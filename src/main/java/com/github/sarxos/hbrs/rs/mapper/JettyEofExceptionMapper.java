package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.eclipse.jetty.io.EofException;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class JettyEofExceptionMapper extends AbstractExceptionMapper<EofException> {

	@Override
	public Response toResponse(EofException exception) {
		return super.build(exception, Status.BAD_REQUEST);
	}
}
