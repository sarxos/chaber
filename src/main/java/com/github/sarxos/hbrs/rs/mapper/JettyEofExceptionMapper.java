package com.github.sarxos.hbrs.rs.mapper;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.eclipse.jetty.io.EofException;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;


@Provider
public class JettyEofExceptionMapper extends AbstractExceptionMapper<EofException> {

	private static final Logger LOG = LoggerFactory.getLogger(JettyEofExceptionMapper.class);

	@Inject
	private javax.inject.Provider<ContainerRequest> containerRequestProvider;

	@Override
	public Response toResponse(EofException exception) {

		return super.build(exception, Status.BAD_REQUEST);
	}
}
