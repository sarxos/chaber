package com.github.sarxos.hbrs.hb.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import org.hibernate.annotations.ManyToAny;
import org.reflections.ReflectionUtils;


public class HibernateUtils {

	private static final Map<Class<?>, Collection<Field>> FIELDS = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static <T> T setRelationsNull(T object) {

		Class<?> clazz = object.getClass();
		Collection<Field> fields = FIELDS.get(clazz);

		if (fields == null) {

			fields = new ArrayList<>();
			fields.addAll(ReflectionUtils.getAllFields(clazz, ReflectionUtils.withAnnotation(ManyToMany.class)));
			fields.addAll(ReflectionUtils.getAllFields(clazz, ReflectionUtils.withAnnotation(ManyToOne.class)));
			fields.addAll(ReflectionUtils.getAllFields(clazz, ReflectionUtils.withAnnotation(OneToMany.class)));
			fields.addAll(ReflectionUtils.getAllFields(clazz, ReflectionUtils.withAnnotation(OneToOne.class)));
			fields.addAll(ReflectionUtils.getAllFields(clazz, ReflectionUtils.withAnnotation(ManyToAny.class)));

			FIELDS.put(clazz, fields);
		}

		for (Field field : fields) {
			field.setAccessible(true);
			try {
				field.set(object, null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException("Unable to set null value on field " + field);
			}
		}

		return object;
	}
}
