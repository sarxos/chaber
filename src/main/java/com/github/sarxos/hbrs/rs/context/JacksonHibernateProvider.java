package com.github.sarxos.hbrs.rs.context;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate3.Hibernate3Module;


/**
 * This is a Jersey context resolver registering Hibernate module in Jackson mapper. By doing this
 * Jackson will not fetch Hibernate lazy collections.
 *
 * @author Bartosz Firyn (bfiryn)
 */
@Provider
public class JacksonHibernateProvider implements ContextResolver<ObjectMapper> {

	private static final Logger LOG = LoggerFactory.getLogger(JacksonHibernateProvider.class);

	private final ObjectMapper mapper;

	public JacksonHibernateProvider() {

		LOG.debug("Create JSON object mapper for context");

		mapper = new ObjectMapper();
		mapper.registerModule(new Hibernate3Module());
		mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
	}

	@Override
	public ObjectMapper getContext(Class<?> type) {

		LOG.debug("Get JSON object mapper");

		return mapper;
	}
}
