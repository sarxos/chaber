package com.github.sarxos.hbrs.hb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
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
public class SchemaUtil {

	public static final class Configuration {

		String clazz;
		String user;
		String password;
	}

	public static Configuration getConfiguration() {

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
			Document doc = builder.parse(SchemaUtil.class.getResourceAsStream("/hibernate.cfg.xml"));
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
	 * Will import schema to the database. Hibernate connection properties will be used to setup
	 * database session.
	 * 
	 * @param file the schema file in form of SQL dump
	 */
	public static final void schemaImport(String file) {

		Configuration c = getConfiguration();

		// String fileName = file.substring(0, file.indexOf('.'));
		// String schemaName = String.format("`%s`", fileName);
		// String schemaNameNew = String.format("`%stest`", fileName);

		String s = new String();
		StringBuffer sb = new StringBuffer();

		InputStream is = null;

		try {

			// make sure to not have line starting with "/*" or any other non
			// alphabetical character

			File f = new File(file);
			if (f.exists()) {
				is = new FileInputStream(file);
			} else {
				is = SchemaUtil.class.getResourceAsStream("/" + file);
			}
			if (is == null) {
				throw new FileNotFoundException(file + " has not been found in directory and classpath");
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			while ((s = br.readLine()) != null) {
				if (s.startsWith("--")) {
					continue;
				}
				if (s.trim().isEmpty()) {
					continue;
				}

				// s = s.replaceAll(schemaName, schemaNameNew);
				s = s.replaceAll("\t", "    ");

				sb.append(s).append('\n');
			}

			br.close();

			// here is our splitter. we use ";" as a delimiter for each request
			// then we are sure to have well formed statements

			String[] instuctions = sb.toString().split(";");

			Connection connection = null;
			Statement stmt = null;

			try {

				connection = DriverManager.getConnection("jdbc:mysql://localhost", c.user, c.password);

				stmt = connection.createStatement();

				System.out.print("IMPORT SCHEMA:");

				for (int i = 0; i < instuctions.length; i++) {

					// we ensure that there is no spaces before or after the
					// request string in order to not execute empty statements

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

				connection.commit();

			} finally {

				if (stmt != null) {
					stmt.close();
				}

				if (connection != null) {
					connection.close();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

	}
}
