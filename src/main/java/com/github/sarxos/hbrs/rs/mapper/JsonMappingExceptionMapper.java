package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.JsonMappingException;


@Provider
public class JsonMappingExceptionMapper extends StreamCopyAbstractMapper<JsonMappingException> {

	@Override
	public Response toResponse(JsonMappingException exception) {
		return super.toResponse(exception);
	}
}
