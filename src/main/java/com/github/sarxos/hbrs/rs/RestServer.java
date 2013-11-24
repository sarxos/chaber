package com.github.sarxos.hbrs.rs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * Standalone REST server. Just run it baby.
 * 
 * @author Bartosz Firyn (bfiryn)
 */
public class RestServer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(RestServer.class);

	private AtomicBoolean started = new AtomicBoolean();
	private AtomicBoolean initialized = new AtomicBoolean();

	private String webApplicationPath = null;

	private int port = 8080;

	/**
	 * Set web application path.
	 * 
	 * @param path
	 */
	public void setWebApplicationPath(String path) {

		if (path == null) {
			throw new IllegalArgumentException("Web application path cannot be null");
		}
		if (path.isEmpty()) {
			throw new IllegalArgumentException("Web application path cannot be empty");
		}

		File webdir = new File(path);
		File webinf = new File(webdir, "WEB-INF");
		File webxml = new File(webinf, "web.xml");

		if (!webdir.exists()) {
			throw new IllegalArgumentException(String.format("Web application path '%s' does not exist", webdir));
		}
		if (!webdir.isDirectory()) {
			throw new IllegalArgumentException(String.format("Web application path '%s' is not a directory", webdir));
		}
		if (!webinf.exists()) {
			throw new IllegalArgumentException(String.format("WEB-INF '%s' does not exist", webdir));
		}
		if (!webinf.isDirectory()) {
			throw new IllegalArgumentException(String.format("WEB-INF '%s' is not a directory", webdir));
		}
		if (!webxml.exists()) {
			throw new IllegalArgumentException(String.format("Web config '%s' does not exist", webdir));
		}
		if (!webxml.isFile()) {
			throw new IllegalArgumentException(String.format("Web config '%s' is not a file", webdir));
		}

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(true);

		DocumentBuilder builder = null;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Cannot create XML document factory", e);
		}

		builder.setErrorHandler(new ErrorHandler() {

			@Override
			public void warning(SAXParseException e) throws SAXException {
				LOG.warn(e.getMessage(), e);
			}

			@Override
			public void error(SAXParseException e) throws SAXException {
				LOG.error(e.getMessage(), e);
			}

			@Override
			public void fatalError(SAXParseException e) throws SAXException {
				LOG.error(e.getMessage(), e);
			}
		});

		Reader r = null;
		try {
			builder.parse(new InputSource(r = new FileReader(webxml)));
		} catch (SAXException e) {
			throw new IllegalArgumentException(String.format("Servlet config in '%s' is not a valid XML", webxml), e);
		} catch (IOException e) {
			throw new RuntimeException(String.format("Cannot read '%s' file", webxml), e);
		} finally {
			if (r != null) {
				try {
					r.close();
				} catch (IOException e) {
					throw new RuntimeException("Cannot close reader", e);
				}
			}
		}

		this.webApplicationPath = path;
	}

	/**
	 * Get port number.
	 * 
	 * @return Return port number
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Set new port number to be used by connector.
	 * 
	 * @param port the port number
	 */
	public void setPort(int port) {
		if (started.get()) {
			throw new IllegalStateException("Cannot change port number when server is started");
		} else {
			this.port = port;
		}
	}

	public void start() {

		if (webApplicationPath == null) {
			throw new IllegalStateException("Web application path has not been defined");
		}

		if (started.compareAndSet(false, true)) {

			Thread t = new Thread(this);
			t.setDaemon(true);
			t.start();

			do {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (!initialized.get());
		}
	}

	public void stop() {
		if (started.compareAndSet(true, false)) {
			do {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (initialized.get());
		}
	}

	@Override
	public void run() {

		SocketConnector connector = new SocketConnector();
		connector.setMaxIdleTime(1000 * 60 * 60); // long timeout (1h)
		connector.setSoLingerTime(-1);
		connector.setPort(port);

		Server server = new Server();
		server.setConnectors(new Connector[] { connector });

		WebAppContext ctx = new WebAppContext();
		ctx.setServer(server);
		ctx.setContextPath("/");
		ctx.setWar(webApplicationPath);

		server.addHandler(ctx);
		server.setSendServerVersion(false);

		try {
			server.start();

			initialized.set(true);

			while (started.get()) {
				Thread.sleep(100);
			}

			server.stop();
			server.join();

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			initialized.set(false);
		}
	}
}
