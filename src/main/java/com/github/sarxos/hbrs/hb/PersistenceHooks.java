package com.github.sarxos.hbrs.hb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.EmptyInterceptor;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;


public class PersistenceHooks extends EmptyInterceptor {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(PersistenceHooks.class);

	private static final Map<String, Boolean> ENABLED = new HashMap<String, Boolean>();
	private static final Map<String, Set<Method>> HOOKS = new HashMap<String, Set<Method>>();

	@SuppressWarnings("unchecked")
	private static final Set<Method> getMethods(Class<?> clazz, Class<? extends Annotation> a) {

		String key = String.format("%s@%s", clazz.getName(), a.getName());

		Boolean enabled = ENABLED.get(key);

		if (Boolean.FALSE.equals(enabled)) {
			return Collections.emptySet();
		}

		Set<Method> hooks = HOOKS.get(key);

		if (hooks == null) {
			HOOKS.put(key, hooks = ReflectionUtils.getMethods(clazz, Predicates.and(ReflectionUtils.withAnnotation(a))));
		}
		if (enabled == null) {
			ENABLED.put(key, !hooks.isEmpty());
		}

		return hooks;
	}

	public static final void hook(Object entity, Class<? extends Annotation> a) {

		if (entity == null) {
			LOG.debug("Null entity passed to invoke hooks");
			return;
		}

		for (Method m : getMethods(entity.getClass(), a)) {
			try {
				m.invoke(entity, new Object[0]);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
