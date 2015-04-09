package com.github.sarxos.hbrs.hb.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;

import com.github.sarxos.hbrs.hb.Identity;


public class HibernateIdentityEnumType implements UserType, ParameterizedType {

	private static final Map<Class<? extends Number>, Integer> TYPES = new HashMap<>();
	static {
		TYPES.put(Byte.class, Types.TINYINT);
		TYPES.put(Integer.class, Types.INTEGER);
		TYPES.put(Long.class, Types.BIGINT);
	}

	private Class<? extends Enum<?>> clazz = null;
	private Class<? extends Number> record = null;

	@Override
	public int[] sqlTypes() {
		return new int[] { TYPES.get(record) };
	}

	@Override
	public Class<? extends Enum<?>> returnedClass() {
		return clazz;
	}

	@Override
	public boolean equals(Object x, Object y) throws HibernateException {
		return x == y;
	}

	@Override
	public int hashCode(Object x) throws HibernateException {
		return x == null ? -1 : x.hashCode();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object nullSafeGet(ResultSet rs, String[] names, Object owner) throws HibernateException, SQLException {

		Number value = rs.getByte(names[0]);
		if (rs.wasNull()) {
			return null;
		}

		for (Enum<?> constant : clazz.getEnumConstants()) {
			Number id = ((Identity<? extends Number>) constant).getId();
			if (id.equals(value)) {
				return constant;
			}
		}

		throw new NoSuchElementException("No such element (" + value + ") in " + clazz);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {

		if (value == null) {
			return;
		}

		Number id = ((Identity<? extends Number>) value).getId();
		st.setObject(index, id);
	}

	@Override
	public Object deepCopy(Object value) throws HibernateException {
		return value;
	}

	@Override
	public boolean isMutable() {
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Serializable disassemble(Object value) throws HibernateException {
		return ((Identity<? extends Serializable>) value).getId();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object assemble(Serializable cached, Object owner) throws HibernateException {

		for (Enum<?> constant : clazz.getEnumConstants()) {
			Number id = ((Identity<? extends Number>) constant).getId();
			if (id.equals(cached)) {
				return constant;
			}
		}

		throw new NoSuchElementException("No such element (" + cached + ") in " + clazz);
	}

	@Override
	public Object replace(Object original, Object target, Object owner) throws HibernateException {
		return original;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setParameterValues(Properties parameters) {

		String t = parameters.getProperty("type");
		try {
			clazz = (Class<? extends Enum<?>>) Class.forName(t).asSubclass(Enum.class);
		} catch (ClassNotFoundException e) {
			throw new HibernateException("Enum class not found", e);
		}

		String r = parameters.getProperty("record");
		try {
			record = Class.forName(r).asSubclass(Number.class);
		} catch (ClassNotFoundException e) {
			throw new HibernateException("Record class not found", e);
		}
	}
}
