package com.github.sarxos.hbrs.rs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractResource {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractResource.class);

	@Context
	protected HttpServletRequest request;

	@Context
	protected HttpServletResponse response;

	@Context
	protected SecurityContext security;

	public HttpServletRequest getRequest() {
		return request;
	}

	public HttpServletResponse getResponse() {
		return response;
	}

	/**
	 * Return information saying that specific operation is forbidden.
	 * 
	 * @return Forbidden response (HTTP 403)
	 */
	protected static Response forbidden(Object message) {
		return Response
			.status(Status.FORBIDDEN)
			.entity(toMessage("error", message))
			.build();
	}

	/**
	 * Return information saying that operation caller is not authorized to
	 * perform it.
	 * 
	 * @return Unauthorized response (HTTP 401)
	 */
	protected static Response unauthorized(String message) {
		return Response
			.status(Status.UNAUTHORIZED)
			.entity(toMessage("error", message))
			.build();
	}

	/**
	 * Return information saying that caller send invalid request (e.g. entity
	 * to add which already exists in database).
	 * 
	 * @return Bad request response (HTTP 400)
	 */
	protected static Response bad(String message) {
		return Response
			.status(Status.BAD_REQUEST)
			.entity(toMessage("error", message))
			.build();
	}

	/**
	 * Return information saying that operation caller is not authorized to
	 * perform it.
	 * 
	 * @return Unauthorized response (HTTP 401)
	 */
	protected static Response unauthorized() {
		return Response
			.status(Status.UNAUTHORIZED)
			.entity(toMessage("error", "Unauthorized"))
			.build();
	}

	private static Map<String, Object> toMessage(String key, Object message) {
		Map<String, Object> r = new HashMap<String, Object>();
		r.put(key, message);
		return r;
	}

	protected static Response missing(String format, Object... args) {
		return missing(String.format(format, args));
	}

	/**
	 * Return information saying that specific resource has not been found or
	 * does not exist.
	 * 
	 * @param message the optional message to be included in response
	 * @return Missing response (HTTP 404)
	 */
	protected static Response missing(String message) {
		return Response
			.status(Status.NOT_FOUND)
			.entity(toMessage("error", message))
			.build();
	}

	/**
	 * Response saying that everything is OK.
	 * 
	 * @param object the object to be marshaled into response
	 * @return OK response (HTTP 200)
	 */
	protected static Response ok(Object object) {

		if (object instanceof String) {
			try {
				object = new ObjectMapper().writeValueAsString(object);
			} catch (JsonProcessingException e) {
				LOG.error(e.getMessage(), e);
				throw new IllegalArgumentException("Unserializable content");
			}
		}

		return Response
			.status(Status.OK)
			.entity(object)
			.build();
	}

	protected static Response removed(Serializable id) {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("removed", id);
		return ok(response);
	}

	/**
	 * Response saying that resource has been created.
	 * 
	 * @param object the created object to be marshaled into response
	 * @return Created response (HTTP 201)
	 */
	protected static Response created(Object object) {
		return Response
			.status(Status.CREATED)
			.entity(object)
			.build();
	}

	/**
	 * Response saying that resource has been accepted for processing but
	 * response is not yet available.
	 * 
	 * @param pkey the processing key
	 * @return Accepted response (HTTP 202)
	 */
	protected static Response accepted(Object pkey) {
		return Response
			.status(Status.ACCEPTED)
			.entity(pkey)
			.build();
	}

	protected boolean isLocalInvocation() {
		return "127.0.0.1".equals(request.getRemoteAddr()) || "localhost".equals(request.getRemoteHost());
	}
}
