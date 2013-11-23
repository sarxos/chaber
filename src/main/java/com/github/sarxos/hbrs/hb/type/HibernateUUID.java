package com.github.sarxos.hbrs.hb.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;


public class HibernateUUID implements UserType {

	@Override
	public int[] sqlTypes() {
		return new int[] { Types.VARCHAR };
	}

	@Override
	public Class<?> returnedClass() {
		return UUID.class;
	}

	@Override
	public boolean equals(Object x, Object y) throws HibernateException {
		return x == null ? y == null : x.equals(y);
	}

	@Override
	public int hashCode(Object x) throws HibernateException {
		return x == null ? -1 : x.hashCode();
	}

	@Override
	public Object nullSafeGet(ResultSet rs, String[] names, Object owner) throws HibernateException, SQLException {
		String value = rs.getString(names[0]);
		return value == null ? null : UUID.fromString(value);
	}

	@Override
	public void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {

		if (value == null) {
			st.setNull(index, Types.VARCHAR);
			return;
		}

		if (!(value instanceof UUID)) {
			throw new IllegalArgumentException(String.format("Value must be %s, is %s", UUID.class, value.getClass()));
		}

		st.setString(index, ((UUID) value).toString());
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
	public Serializable disassemble(Object value) throws HibernateException {
		return value == null ? null : ((UUID) value).toString();
	}

	@Override
	public Object assemble(Serializable cached, Object owner) throws HibernateException {
		return cached == null ? null : UUID.fromString(cached.toString());
	}

	@Override
	public Object replace(Object original, Object target, Object owner) throws HibernateException {
		return original;
	}
}
