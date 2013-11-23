package com.github.sarxos.hbrs.hb.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.sql.Statement;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;


/**
 * Utility class to import database schema.
 * 
 * @author Bartosz Firyn (bfiryn)
 */
public class SchemaImporter {

	private static final class Configuration {

		String clazz;
		String user;
		String password;
	}

	private static Configuration getConfiguration() {

		Configuration c = new Configuration();

		try {

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);
			dbf.setNamespaceAware(true);
			dbf.setFeature("http://xml.org/sax/features/namespaces", false);
			dbf.setFeature("http://xml.org/sax/features/validation", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document doc = builder.parse(SchemaImporter.class.getResourceAsStream("/hibernate.cfg.xml"));
			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();

			c.clazz = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='hibernate.connection.driver_class']/text()").evaluate(doc, XPathConstants.STRING);
			c.user = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='hibernate.connection.username']/text()").evaluate(doc, XPathConstants.STRING);
			c.password = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='hibernate.connection.password']/text()").evaluate(doc, XPathConstants.STRING);

			Class.forName(c.clazz).newInstance();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return c;
	}

	/**
	 * Will import schema to the database. Hibernate connection properties will
	 * be used to setup database session.
	 * 
	 * @param file the schema file in form of SQL dump
	 */
	public static void process(String file) {

		Configuration c = getConfiguration();

		String fileName = file.substring(0, file.indexOf('.'));
		String schemaName = String.format("`%s`", fileName);
		String schemaNameNew = String.format("`%stest`", fileName);

		String s = new String();
		StringBuffer sb = new StringBuffer();

		try {

			// make sure to not have line starting with "/*" or any other non
			// alphabetical character

			InputStream is = SchemaImporter.class.getResourceAsStream("/" + file);
			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			while ((s = br.readLine()) != null) {
				if (s.startsWith("--")) {
					continue;
				}
				if (s.trim().isEmpty()) {
					continue;
				}

				s = s.replaceAll(schemaName, schemaNameNew);

				sb.append(s).append('\n');
			}

			br.close();

			// here is our splitter. we use ";" as a delimiter for each request
			// then we are sure to have well formed statements

			String[] instuctions = sb.toString().split(";");
			Statement stmt = DriverManager.getConnection("jdbc:mysql://localhost", c.user, c.password).createStatement();

			System.out.print("IMPORT SCHEMA:");

			for (int i = 0; i < instuctions.length; i++) {

				// we ensure that there is no spaces before or after the request
				// string in order to not execute empty statements

				if (instuctions[i].trim().isEmpty()) {
					continue;
				}

				stmt.executeUpdate(instuctions[i]);

				String[] lines = instuctions[i].split("\n");
				for (String line : lines) {
					if (line.trim().isEmpty()) {
						continue;
					}
					System.out.format("\nmysql> %s", line);
				}

				System.out.print(';');
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
