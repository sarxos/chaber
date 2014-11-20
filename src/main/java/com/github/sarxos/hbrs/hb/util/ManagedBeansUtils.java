package com.github.sarxos.hbrs.hb.util;

import java.lang.management.ManagementFactory;

import javax.annotation.ManagedBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ManagedBeansUtils {

	private static final Logger LOG = LoggerFactory.getLogger(ManagedBeansUtils.class);

	private static final MBeanServer MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

	private static String getSimpleName(Object mbean) {
		return getSimpleName(mbean.getClass());
	}

	private static String getSimpleName(Class<?> clazz) {

		ManagedBean mb = clazz.getAnnotation(ManagedBean.class);
		if (mb == null) {
			throw new IllegalArgumentException("Class " + clazz + " is not annotated with " + ManagedBean.class);
		}

		String name = mb.value();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("No managed bean name defined for class " + clazz);
		}

		return name;
	}

	private static ObjectName getObjectName(String name, Object mbean) {
		try {
			return new ObjectName(mbean.getClass().getPackage().getName() + ":name=" + name);
		} catch (MalformedObjectNameException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static void register(String name, Object mbean) {

		ObjectName oname = getObjectName(name, mbean);

		LOG.debug("Registering managed bean {} with name {}", mbean, oname);

		try {
			MBEAN_SERVER.registerMBean(mbean, oname);
		} catch (MBeanRegistrationException | NotCompliantMBeanException e) {
			throw new RuntimeException(e);
		} catch (InstanceAlreadyExistsException e) {

			LOG.debug("Removing already existing mbean {}", oname);

			try {
				MBEAN_SERVER.unregisterMBean(oname);
				MBEAN_SERVER.registerMBean(mbean, oname);
			} catch (MBeanRegistrationException | InstanceNotFoundException | InstanceAlreadyExistsException | NotCompliantMBeanException e1) {
				throw new RuntimeException(e1);
			}
		}
	}

	public static void unregister(String name, Object mbean) {

		ObjectName oname = getObjectName(name, mbean);

		LOG.debug("Unregistering managed bean {}", oname);

		try {
			MBEAN_SERVER.unregisterMBean(oname);
		} catch (MBeanRegistrationException | InstanceNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static void register(Object mbean) {
		register(getSimpleName(mbean), mbean);
	}

	public static void unregister(Object mbean) {
		unregister(getSimpleName(mbean), mbean);
	}

	public static Object invoke(Class<?> klass, String method, Object[] params, String[] signature) {
		try {
			return MBEAN_SERVER.invoke(getObjectName(getSimpleName(klass), klass), method, params, signature);
		} catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static ObjectName getObjectName(String name, Class<?> klass) {
		try {
			return new ObjectName(klass.getPackage().getName() + ":name=" + name);
		} catch (MalformedObjectNameException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
