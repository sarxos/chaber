package com.github.sarxos.hbrs.rs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ConnectorStatistics;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.servlet.WebComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * Standalone Jetty server. Just run it baby.
 *
 * @author Bartosz Firyn (bfiryn)
 */
public abstract class AbstractServer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractServer.class);

	private AtomicBoolean started = new AtomicBoolean();
	private AtomicBoolean initialized = new AtomicBoolean();

	private String webApplicationPath = null;

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

	private File getWebXmlFile(String path) {

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

		return webxml;
	}

	private DocumentBuilder getDocumentBuilder() {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(true);

		DocumentBuilder builder = null;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("Cannot create XML document factory", e);
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

		return builder;
	}

	/**
	 * Set web application path.
	 *
	 * @param path
	 */
	public void setWebApplicationPath(String path) {

		if (StringUtils.isEmpty(path)) {
			throw new IllegalArgumentException("Web application path cannot be empty");
		}

		File webxml = getWebXmlFile(path);
		DocumentBuilder builder = getDocumentBuilder();

		try (Reader reader = new FileReader(webxml)) {
			builder.parse(new InputSource(reader));
		} catch (SAXException e) {
			throw new IllegalArgumentException(String.format("Servlet config in '%s' is not a valid XML", webxml), e);
		} catch (IOException e) {
			throw new IllegalStateException(String.format("Cannot read '%s' file", webxml), e);
		}

		this.webApplicationPath = path;
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
					LOG.debug("Thread has been interrupted");
					return;
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
					LOG.debug("Thread has been interrupted when doing stop");
					return;
				}
			} while (initialized.get());
		}
	}

	/**
	 * @return The number of acceptor threads.
	 */
	protected abstract int getAcceptorsNumber();

	/**
	 * @return Minimum number of threads in workers pool.
	 */
	protected abstract int getWorkerPoolMin();

	/**
	 * @return Maximum number of threads in workers pool.
	 */
	protected abstract int getWorkerPoolMax();

	/**
	 * @return Workers pool idle time.
	 */
	protected abstract int getWorkerPoolIdleTime();

	/**
	 * @return Workers pool queue size.
	 */
	protected abstract int getWorkerPoolQueueSize();

	/**
	 * @return Connection buffer size.
	 */
	protected abstract int getConnectionBufferSize();

	/**
	 * @return Is HTTP enabled.
	 */
	protected abstract boolean isHttpEnabled();

	/**
	 * @return HTTP port number.
	 */
	protected abstract int getHttpPort();

	/**
	 * @return HTTP connection timeout.
	 */
	protected abstract int getHttpConnectionTimeout();

	/**
	 * @return Is HTTP enabled.
	 */
	protected abstract boolean isHttpsEnabled();

	/**
	 * @return HTTP port number.
	 */
	protected abstract int getHttpsPort();

	/**
	 * @return HTTP connection timeout.
	 */
	protected abstract int getHttpsConnectionTimeout();

	/**
	 * @return Key store password.
	 */
	protected abstract String getKeyStorePassword();

	/**
	 * @return Key manager password.
	 */
	protected abstract String getKeyManagerPassword();

	/**
	 * @return Certificate alias.
	 */
	protected abstract String getCertAliasPassword();

	/**
	 * @return HK2 service locator.
	 */
	protected abstract ServiceLocator getServiceLocator();

	@Override
	public void run() {

		int acceptorsNumber = getAcceptorsNumber();
		int maxAcceptorsNumber = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
		if (acceptorsNumber > maxAcceptorsNumber) {
			LOG.warn("Too high number ({}) of acceptors. Max possible is {} and this value has been set.", acceptorsNumber, maxAcceptorsNumber);
			acceptorsNumber = maxAcceptorsNumber;
		}

		int minThreads = getWorkerPoolMin();
		int maxThreads = getWorkerPoolMax();
		int idleTime = getWorkerPoolIdleTime();
		int queueSize = getWorkerPoolQueueSize();

		QueuedThreadPool qtp = new QueuedThreadPool(maxThreads, minThreads, idleTime, new ArrayBlockingQueue<Runnable>(queueSize));

		Server server = new Server(qtp);

		HttpConfiguration defaultHttpConfig = new HttpConfiguration();
		defaultHttpConfig.setOutputBufferSize(getConnectionBufferSize());
		defaultHttpConfig.setSendServerVersion(false);
		defaultHttpConfig.setSendXPoweredBy(false);
		defaultHttpConfig.setSendDateHeader(true);
		defaultHttpConfig.setSecureScheme("https");

		List<Connector> connectors = new ArrayList<Connector>();

		// if HTTP is enabled

		if (isHttpEnabled()) {

			ServerConnector http = new NetworkTrafficServerConnector(server, new HttpConnectionFactory(defaultHttpConfig));
			http.setPort(getHttpPort());
			http.setIdleTimeout(getHttpConnectionTimeout());
			http.setAcceptQueueSize(1);

			connectors.add(http);
		}

		// if HTTPS is enabled

		if (isHttpsEnabled()) {

			String keystorePath = System.getProperty("jetty.keystore", "src/main/resources/keystore.jks");

			SslContextFactory sslContextFactory = new SslContextFactory();
			sslContextFactory.setKeyStorePath(keystorePath);
			sslContextFactory.setKeyStorePassword(getKeyStorePassword());
			sslContextFactory.setKeyManagerPassword(getKeyManagerPassword());
			sslContextFactory.setCertAlias(getCertAliasPassword());
			sslContextFactory.setExcludeCipherSuites(
				"SSL_RSA_WITH_DES_CBC_SHA",
				"SSL_DHE_RSA_WITH_DES_CBC_SHA",
				"SSL_DHE_DSS_WITH_DES_CBC_SHA",
				"SSL_RSA_EXPORT_WITH_RC4_40_MD5",
				"SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
				"SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
				"SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

			HttpConfiguration httpsConfig = new HttpConfiguration(defaultHttpConfig);
			httpsConfig.setSecurePort(getHttpsPort());
			httpsConfig.addCustomizer(new SecureRequestCustomizer());

			final ConnectorStatistics stats = new ConnectorStatistics();

			ServerConnector https = new NetworkTrafficServerConnector(server, new HttpConnectionFactory(httpsConfig), sslContextFactory);
			https.setPort(getHttpsPort());
			https.setIdleTimeout(getHttpsConnectionTimeout());
			https.setAcceptQueueSize(acceptorsNumber);
			https.addBean(stats);

			connectors.add(https);
		}

		WebAppContext ctx = new WebAppContext();
		ctx.setServer(server);
		ctx.setContextPath("/");
		ctx.setWar(webApplicationPath);
		ctx.setAttribute(WebComponent.SERVICE_LOCATOR_ATTRIBUTE, getServiceLocator());

		server.setConnectors(connectors.toArray(new Connector[connectors.size()]));
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
