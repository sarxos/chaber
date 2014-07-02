package com.github.sarxos.hbrs.rs.mapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;
import com.github.sarxos.hbrs.rs.filter.RequestStreamCopyFilter;


public abstract class StreamCopyAbstractMapper<T extends Throwable> extends AbstractExceptionMapper<T> {

	private static final Logger LOG = LoggerFactory.getLogger(StreamCopyAbstractMapper.class);

	@Inject
	private javax.inject.Provider<ContainerRequest> containerRequestProvider;

	@Override
	public Response toResponse(T exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		String message = exception.getMessage().replaceAll("\\n", "");
		Object stream = containerRequestProvider.get().getProperty(RequestStreamCopyFilter.PROPERTY);

		if (stream != null) {

			ByteArrayOutputStream baos = (ByteArrayOutputStream) stream;

			String payload = null;
			try {
				payload = baos.toString();
			} finally {
				try {
					baos.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}

			if (payload != null) {
				LOG.warn("{} caused by invalid payload: {}", message, payload);
			} else {
				LOG.error("The payload object from stream is null! Exception is: {}", message);
			}

		} else {
			LOG.warn(message);
		}

		return super.build(exception, Status.BAD_REQUEST);
	}
}
