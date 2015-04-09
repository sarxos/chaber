package com.github.sarxos.hbrs.hb;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.hibernate.jdbc.Work;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.mse.SchemaEvolver;


/**
 * Hibernate work to perform database schema evolution.
 */
public class EvolutionWork implements Work {

	private static final Logger LOG = LoggerFactory.getLogger(EvolutionWork.class);

	private final String db;

	private final Collection<String> paths;

	@SuppressWarnings("unchecked")
	public EvolutionWork(String db) {
		this(db, Collections.EMPTY_LIST);
	}

	public EvolutionWork(String db, Collection<String> paths) {
		this.db = db;
		this.paths = new ArrayList<>(Arrays.asList("db/" + db, "src/main/resources/db/" + db, "target/db/" + db));
		this.paths.addAll(paths);
	}

	private String getSchemaDirectoryPath() {
		for (String path : paths) {
			if (new File(path).exists()) {
				return path;
			}
		}
		throw new IllegalStateException("No " + db + " directory has been found in " + paths);
	}

	@Override
	public void execute(Connection conn) throws SQLException {

		String path = getSchemaDirectoryPath();

		LOG.debug("The schema directory path is {}", path);

		try {
			new SchemaEvolver(conn).evolve(path);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
