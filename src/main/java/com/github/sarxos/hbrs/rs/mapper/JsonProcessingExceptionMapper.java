package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.JsonProcessingException;


@Provider
public class JsonProcessingExceptionMapper extends StreamCopyAbstractMapper<JsonProcessingException> {

	@Override
	public Response toResponse(JsonProcessingException exception) {
		return super.toResponse(exception);
	}
}
