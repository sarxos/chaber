package com.github.sarxos.hbrs.hb;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;


/**
 * Custom annotation configuration for Hibernate.
 *
 * @author Bartosz Firyn (sarxos)
 */
public abstract class FilterableAnnotationConfiguration extends AnnotationConfiguration {

	private static final long serialVersionUID = 1L;

	/**
	 * Used to return filtered properties.
	 *
	 * @author Bartosz Firyn (sarxos)
	 */
	public static class PropertyEntry {

		/**
		 * The property name.
		 */
		private final String name;

		/**
		 * The property value.
		 */
		private final String value;

		/**
		 * Create object with property name and value.
		 *
		 * @param name the property name
		 * @param value the property value
		 */
		public PropertyEntry(String name, String value) {
			this.name = name;
			this.value = value;
		}

		/**
		 * @return The property name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return The property value
		 */
		public String getValue() {
			return value;
		}
	}

	/**
	 * Get method by reflection.
	 *
	 * @param name the method name
	 * @param types the types
	 * @return Method
	 */
	private Method getMethod(String name, Class<?>... types) {
		try {
			Method method = Configuration.class.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method;
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Cannot reflect method " + name + " with args " + Arrays.toString(types));
		}
	}

	/**
	 * Invoke method by reflection.
	 *
	 * @param m the method
	 * @param args the arguments
	 * @return Returned value
	 */
	private Object doMethod(Method m, Object... args) {
		try {
			return m.invoke(this, args);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException("Unable to invoke " + m.getName() + " on " + getClass() + " with args " + Arrays.toString(args));
		}
	}

	/**
	 * The parseSessionFactory method.
	 */
	private final Method parseSessionFactory = getMethod("parseSessionFactory", Element.class, String.class);

	/**
	 * The parseSecurity method.
	 */
	private final Method parseSecurity = getMethod("parseSecurity", Element.class);

	/**
	 * This method is being invoked for every property from the hibernate configuration file and
	 * return {@link PropertyEntry} object which holds results of property filtering.
	 *
	 * @param name the property name
	 * @param value the property value
	 * @return The filtered property
	 */
	protected abstract PropertyEntry filter(String name, String value);

	@Override
	protected AnnotationConfiguration doConfigure(Document doc) throws HibernateException {

		Element sfNode = doc.getRootElement().element("session-factory");
		String name = sfNode.attributeValue("name");
		if (name != null) {
			getProperties().setProperty(Environment.SESSION_FACTORY_NAME, name);
		}

		parseProperties(sfNode);

		doMethod(parseSessionFactory, sfNode, name);

		Element secNode = doc.getRootElement().element("security");
		if (secNode != null) {
			doMethod(parseSecurity, secNode);
		}

		return this;
	}

	/**
	 * Parse properties from XML.
	 *
	 * @param parent the XML element holding property tags
	 */
	private void parseProperties(Element parent) {

		List<PropertyEntry> properties = new ArrayList<>();
		Iterator<?> iter = parent.elementIterator("property");

		while (iter.hasNext()) {

			Element node = (Element) iter.next();
			String name = node.attributeValue("name");
			String value = node.getText().trim();

			properties.add(filter(name, value));

			if (!name.startsWith("hibernate")) {
				name = "hibernate." + name;
				properties.add(filter(name, value));
			}
		}

		applyProperties(properties);

		Environment.verifyProperties(getProperties());
	}

	/**
	 * Apply properties to Hibernate.
	 *
	 * @param properties
	 */
	protected void applyProperties(List<PropertyEntry> properties) {
		for (PropertyEntry pe : properties) {
			getProperties().setProperty(pe.getName(), pe.getValue());
		}
	}
}
