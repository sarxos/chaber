package com.github.sarxos.hbrs.hb.type;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;


public class HibernateInetAddress implements UserType {

	@Override
	public int[] sqlTypes() {
		return new int[] { Types.VARCHAR };
	}

	@Override
	public Class<?> returnedClass() {
		return InetAddress.class;
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
		try {
			return value == null ? null : InetAddress.getByName(value);
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void nullSafeSet(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {

		if (value == null) {
			st.setNull(index, Types.VARCHAR);
			return;
		}

		if (!(value instanceof InetAddress)) {
			throw new IllegalArgumentException(String.format("Value must be %s, is %s", InetAddress.class, value.getClass()));
		}

		st.setString(index, ((InetAddress) value).getHostAddress());
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
		return value == null ? null : ((InetAddress) value).getHostAddress();
	}

	@Override
	public Object assemble(Serializable cached, Object owner) throws HibernateException {
		try {
			return cached == null ? null : InetAddress.getByName(cached.toString());
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Object replace(Object original, Object target, Object owner) throws HibernateException {
		return original;
	}
}
