package com.github.sarxos.hbrs.rs.mapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.sarxos.hbrs.rs.AbstractExceptionMapper;
import com.github.sarxos.hbrs.rs.filter.RequestStreamCopyFilter;


@Provider
public class JsonParseExceptionMapper extends AbstractExceptionMapper<JsonParseException> {

	private static final Logger LOG = LoggerFactory.getLogger(JsonParseExceptionMapper.class);

	@Inject
	private javax.inject.Provider<ContainerRequest> containerRequestProvider;

	@Override
	public Response toResponse(JsonParseException exception) {

		if (exception == null) {
			throw new IllegalArgumentException("Exception cannot be null");
		}

		Object stream = containerRequestProvider.get().getProperty(RequestStreamCopyFilter.PROPERTY);
		if (stream != null) {

			ByteArrayOutputStream baos = (ByteArrayOutputStream) stream;
			String json = null;

			try {
				json = baos.toString();
				baos.close();
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}

			if (json != null) {
				LOG.warn("Invalid JSON received: {}", json);
			} else {
				LOG.error("The JSON object from stream is null!");
			}

		} else {
			LOG.warn(exception.getMessage().replaceAll("\\n", ""));
		}

		return super.build(exception, Status.BAD_REQUEST);
	}
}
