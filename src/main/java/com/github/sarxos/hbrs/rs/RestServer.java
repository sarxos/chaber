package com.github.sarxos.hbrs.rs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.nio.NetworkTrafficSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
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

	private int port = 8081;

	private Connector connector = null;

	public void setConnector(Connector connector) {
		this.connector = connector;
	}

	public Connector getConnector() {
		if (connector == null) {
			throw new IllegalStateException("Connector cannot be null!");
		}
		return connector;
	}

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

		builder.setEntityResolver(new EntityResolver() {

			@Override
			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
				if (systemId.contains("web-app_2_3.dtd")) {
					return new InputSource(getClass().getResourceAsStream("/web-app_2_3.dtd"));
				} else {
					return null;
				}
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

			LOG.info("Starting embedded Chaber server");

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

		int poolSize = Runtime.getRuntime().availableProcessors() - 1;
		if (poolSize < 1) {
			poolSize = 1;
		}

		Server server = new Server();

		HttpConfiguration http_config = new HttpConfiguration();
		http_config.setSecureScheme("https");
		http_config.setSecurePort(8443);
		http_config.setOutputBufferSize(32768);
		http_config.setSendServerVersion(false);
		http_config.setSendXPoweredBy(false);

		ServerConnector http = new NetworkTrafficSelectChannelConnector(server, new HttpConnectionFactory(http_config));
		http.setPort(8081);
		http.setIdleTimeout(3000);
		http.setAcceptQueueSize(poolSize);

		String keystorePath = System.getProperty("jetty.keystore", "src/main/resources/keystore.jks");

		SslContextFactory sslContextFactory = new SslContextFactory();
		sslContextFactory.setKeyStorePath(keystorePath);
		sslContextFactory.setKeyStorePassword("OBF:1qpb1u2a1xmq1wu01v8s1lz31v9u1wue1xmk1u301qq7");
		sslContextFactory.setKeyManagerPassword("OBF:1qpb1u2a1xmq1wu01v8s1lz31v9u1wue1xmk1u301qq7");
		sslContextFactory.setCertAlias("jetty");

		HttpConfiguration https_config = new HttpConfiguration(http_config);
		https_config.addCustomizer(new SecureRequestCustomizer());

		ServerConnector https = new NetworkTrafficSelectChannelConnector(server, new HttpConnectionFactory(https_config), sslContextFactory);
		https.setPort(8443);
		https.setIdleTimeout(3000);
		https.setAcceptQueueSize(poolSize);

		server.setConnectors(new Connector[] { http, https });

		WebAppContext ctx = new WebAppContext();
		ctx.setServer(server);
		ctx.setContextPath("/");
		ctx.setWar(webApplicationPath);

		server.setHandler(ctx);

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
