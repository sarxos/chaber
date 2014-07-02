package com.github.sarxos.hbrs.rs.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.core.JsonParseException;


@Provider
public class JsonParseExceptionMapper extends StreamCopyAbstractMapper<JsonParseException> {

	@Override
	public Response toResponse(JsonParseException exception) {
		return super.toResponse(exception);
	}
}
