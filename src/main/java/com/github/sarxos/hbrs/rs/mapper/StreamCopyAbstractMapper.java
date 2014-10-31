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

	/**
	 * Get string payload from byte array stream.
	 *
	 * @param stream the stream object
	 * @return Payload
	 */
	private String getPayload(Object stream) {

		if (stream == null) {
			return null;
		}

		if (stream instanceof ByteArrayOutputStream) {
			try (ByteArrayOutputStream baos = (ByteArrayOutputStream) stream) {
				return baos.toString("UTF8");
			} catch (IOException e) {
				throw new IllegalStateException(e); // will never happen
			}
		}

		LOG.error("Invalid object of {} detected in {}", stream.getClass(), getClass());

		return null;
	}

	@Override
	public Response toResponse(T exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		String message = exception.getMessage().replaceAll("\\n", "");
		Object stream = containerRequestProvider.get().getProperty(RequestStreamCopyFilter.PROPERTY);
		String payload = getPayload(stream);

		if (payload != null) {
			LOG.warn("{} caused by invalid payload: {}", message, payload);
		} else {
			LOG.warn(message);
		}

		return super.build(exception, Status.BAD_REQUEST);
	}
}
