package com.github.sarxos.hbrs.rs.context;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate3.Hibernate3Module;


/**
 * This is a Jersey context resolver registering Hibernate module in Jackson
 * mapper. By doing this Jackson will not fetch Hibernate lazy collections.
 * 
 * @author Bartosz Firyn (bfiryn)
 */
@Provider
public class JacksonHibernateProvider implements ContextResolver<ObjectMapper> {

	@Override
	public ObjectMapper getContext(Class<?> type) {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new Hibernate3Module());
		mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
		return mapper;
	}
}