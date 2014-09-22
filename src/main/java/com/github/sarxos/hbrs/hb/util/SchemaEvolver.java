package com.github.sarxos.hbrs.hb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.collections4.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MySQL schema upgrade tool.
 * 
 * @author Bartosz Firyn (sarxos)
 */
public class SchemaEvolver {

	/**
	 * Logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(SchemaEvolver.class);

	/**
	 * Used to compare two schema version strings.
	 * 
	 * @author Bartosz Firyn (sarxos)
	 */
	private static final class VersionComparator implements Comparator<String> {

		@Override
		public int compare(String a, String b) {

			String[] ap = a.split("\\.");
			String[] bp = b.split("\\.");

			if (ap.length != bp.length || ap.length != 7) {
				throw new IllegalStateException("Invalid schema number");
			}

			int ai = 0;
			int bi = 0;

			for (int i = 0; i < ap.length; i++) {

				ai = Integer.parseInt(ap[i]);
				bi = Integer.parseInt(bp[i]);

				if (ai == bi) {
					continue;
				}
				return ai - bi;
			}

			return 0;
		}
	}

	/**
	 * Used to filter schema version directories.
	 * 
	 * @author Bartosz Firyn (sarxos)
	 */
	private static final class EvolutionDirFilter implements FilenameFilter {

		@Override
		public boolean accept(File dir, String name) {

			String[] parts = name.split("\\.");

			if (parts.length != 7) {
				return false;
			}

			for (String part : parts) {
				for (int i = 0; i < part.length(); i++) {
					if (!Character.isDigit(part.charAt(i))) {
						return false;
					}
				}
			}

			if (!new File(dir, name).isDirectory()) {
				return false;
			}

			return true;
		}
	}

	/**
	 * File name filter used to filter schema directories.
	 */
	private static final FilenameFilter EVF = new EvolutionDirFilter();

	/**
	 * Schema version comparator.
	 */
	private static final Comparator<String> VC = new VersionComparator();

	/**
	 * Used to distinguish upgrade.
	 */
	private static final String UPGRADE = "upgrade";

	/**
	 * Used to distinguish downgrade.
	 */
	private static final String DOWNGRADE = "downgrade";

	/**
	 * MySQL database connection.
	 */
	private final Connection connection;

	/**
	 * Create MySQL schema evolver.
	 * 
	 * @param connection the database connection, must not be null
	 */
	public SchemaEvolver(Connection connection) {

		if (connection == null) {
			throw new IllegalArgumentException("Connection cannot be null");
		}

		this.connection = connection;
	}

	/**
	 * Evolve database schema.
	 * 
	 * @param path the path to schema directories
	 * @throws IOException when files cannot be read or is not a directory
	 * @throws SQLException when something wrong happen in SQL 
	 */
	public void evolve(String path) throws IOException, SQLException {

		LOG.info("Starting schema evolution script");

		// verify path points to a directory

		File dir = new File(path);
		if (!dir.isDirectory()) {
			throw new FileNotFoundException(path);
		}

		// verify if last path segment is the same as db name (for sanity purpose)

		String dbname = null;
		try {
			dbname = connection.getCatalog();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		if (!dbname.equals(dir.getName())) {
			throw new IllegalArgumentException("Last path segment must be the same as database name");
		}

		ArrayList<String> versions = new ArrayList<>();
		for (File file : dir.listFiles(EVF)) {
			versions.add(file.getName());
		}

		Collections.sort(versions, VC);

		String current = getCurrentVersion();
		String newest = versions.get(versions.size() - 1);
		String direction = null;

		LOG.info("Current schema version is {} and the newest one is {}", current, newest);

		int result = VC.compare(current, newest);
		Iterator<String> vi = null;

		// return if schema is the newest one

		if (result == 0) {
			LOG.info("Schema version is already the newest one");
			return;
		} else if (result < 0) {
			LOG.info("Schema version should be upgraded");
			direction = UPGRADE;
			vi = versions.listIterator();
		} else {
			LOG.info("Schema version should be downgraded (not implemented yet)");
			direction = DOWNGRADE;
			vi = new ReverseListIterator<>(versions);
		}

		while (vi.hasNext()) {

			String version = vi.next();

			switch (direction) {
				case UPGRADE:
					if (VC.compare(version, current) <= 0) {
						LOG.debug("Skipping {} vs current {}", version, current);
						continue;
					} else {
						LOG.info("Processing schema version {} {}", version, direction);
					}
					break;
				case DOWNGRADE:
					if (VC.compare(current, version) >= 0) {
						LOG.debug("Skipping {} vs current {}", version, current);
						continue;
					} else {
						LOG.info("Processing schema version {} {}", version, direction);
					}
					break;
			}

			File verdir = new File(dir, version);
			File sqlfile = new File(verdir, direction + ".sql");

			if (sqlfile.exists()) {
				evaluate(sqlfile);
			} else {
				LOG.debug("No {} has been found", sqlfile);
			}

			updateVersion(version);
		}
	}

	/**
	 * Evaluate SQL file.
	 * 
	 * @param file the file object
	 * @throws IOException when file cannot be read
	 * @throws SQLException when there is an SQL syntax in given file
	 */
	private final void evaluate(File file) throws IOException, SQLException {

		if (file == null) {
			return;
		}

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {

			@SuppressWarnings("unchecked")
			ImmutablePair<String, Object>[] params = new ImmutablePair[] {
				getParam("UNIQUE_CHECKS", 0),
				getParam("FOREIGN_KEY_CHECKS", 0),
				getParam("SQL_MODE", "TRADITIONAL"),
			};

			String s = null;
			String delimiter = ";";
			StringBuilder sb = new StringBuilder();

			while ((s = br.readLine()) != null) {

				s = s.trim();
				if (s.startsWith("--") || s.isEmpty()) {
					continue;
				}

				s = s.replaceAll("\t", " ");
				s = s.replaceAll("\\s+", " ");
				s = s.trim();

				if (StringUtils.startsWithIgnoreCase(s, "DELIMITER")) {
					delimiter = s.split(" ")[1].trim();
					sb.delete(0, sb.length());
					continue;
				}

				boolean semicolon = false;
				if (s.endsWith(delimiter)) {
					s = s.substring(0, s.length() - delimiter.length());
					semicolon = true;
				}

				sb.append(s).append(' ');

				if (semicolon) {

					String sql = sb.toString();
					LOG.debug("mysql> {}", sql);

					try (Statement stmt = connection.createStatement()) {
						stmt.execute(sql);
					} finally {
						sb.delete(0, sb.length());
					}
				}
			}

			for (ImmutablePair<String, Object> param : params) {
				setParam(param.getLeft(), param.getRight());
			}
		}
	}

	/**
	 * Set connection parameter value.
	 * 
	 * @param name the parameter name
	 * @param value the new parameter value
	 * @throws SQLException when parameter name is invalid
	 */
	private final void setParam(String name, Object value) throws SQLException {
		LOG.debug("mysql> SET {} = {} ", name, value instanceof String ? "'" + value + "'" : value);
		try (PreparedStatement stmt = connection.prepareStatement("SET " + name + " = ?")) {
			stmt.setObject(1, value);
			stmt.execute();
		}
	}

	/**
	 * Get connection parameter
	 * 
	 * @param param the parameter name
	 * @param defValue the parameter default value
	 * @return Parameter value of default one if parameter is not defined
	 * @throws SQLException when parameter name is invalid
	 */
	private final ImmutablePair<String, Object> getParam(String param, Object defValue) throws SQLException {

		String name = "@@" + param;
		String query = "SELECT " + name;
		Object value = null;

		LOG.debug("mysql> {}", query);

		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(query)) {
				if (rs.next()) {
					value = rs.getObject(name);
				} else {
					value = defValue;
				}
			}
		}

		return new ImmutablePair<String, Object>(param, value);
	}

	/**
	 * Get current schema version from database.
	 * 
	 * @return Currently installed database schema version
	 * @throws SQLException
	 */
	private final String getCurrentVersion() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT v.version FROM version v WHERE v.id = 1")) {
				if (rs.next()) {
					return rs.getString(1);
				}
			} catch (SQLException e) {
				LOG.trace(e.getMessage(), e);
				LOG.debug("No version table detected");
			}
		}
		return "00.00.00.00.00.00.000";
	}

	/**
	 * Update version in database.
	 * 
	 * @param version
	 * @throws SQLException
	 */
	private final void updateVersion(String version) throws SQLException {
		LOG.debug("Update version to {}", version);
		try (PreparedStatement stmt = connection.prepareStatement("UPDATE version v SET v.version = ? WHERE v.id = 1")) {
			stmt.setString(1, version);
			stmt.execute();
		}
	}
}
