package com.github.sarxos.hbrs.rs.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.input.TeeInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This filter is to provide input stream copy in case of exception. It works
 * only when debug is enabled for this class.
 * 
 * @author Bartosz Firyn (sarxos)
 */
@Provider
@Priority(Priorities.ENTITY_CODER)
public class RequestStreamCopyFilter implements ContainerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RequestStreamCopyFilter.class);

	public static final String PROPERTY = "ENTITY_STREAM_COPY";

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		if (LOG.isDebugEnabled()) {
			ByteArrayOutputStream proxyOutputStream = new ByteArrayOutputStream();
			requestContext.setEntityStream(new TeeInputStream(requestContext.getEntityStream(), proxyOutputStream));
			requestContext.setProperty(PROPERTY, proxyOutputStream);
		}
	}
}
