package com.github.sarxos.hbrs.rs.context;

import java.lang.reflect.Type;
import java.util.List;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import com.github.sarxos.hbrs.hb.PersistencyKeeper;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.server.impl.inject.AbstractHttpContextInjectable;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.sun.jersey.spi.resource.Singleton;


/**
 * Provides context instances extending {@link PersistencyKeeper} abstraction
 * (or more simple, DB DAOs).
 * 
 * @author Bartosz Firyn (bfiryn)
 */
@Provider
@Singleton
public class PersistencyKeeperProvider extends AbstractHttpContextInjectable<PersistencyKeeper> implements InjectableProvider<Context, Type> {

	private Class<?> type = null;

	@Override
	public Injectable<PersistencyKeeper> getInjectable(ComponentContext ic, Context a, Type c) {

		List<Class<?>> classes = PersistencyKeeper.getDaoClasses();
		if (classes == null) {
			throw new IllegalStateException("Database DAO classes list cannot be null");
		}

		for (Class<?> t : classes) {
			if (c.equals(t)) {
				PersistencyKeeperProvider injectable = (PersistencyKeeperProvider) getInjectable(ic, a);
				injectable.type = t;
				return injectable;
			}
		}
		return null;
	}

	public Injectable<PersistencyKeeper> getInjectable(ComponentContext ic, Context a) {
		return this;
	}

	@Override
	public ComponentScope getScope() {
		return ComponentScope.PerRequest;
	}

	@Override
	public PersistencyKeeper getValue(HttpContext c) {
		if (type == null) {
			throw new IllegalStateException("Type has not been found");
		}
		try {
			return (PersistencyKeeper) type.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
