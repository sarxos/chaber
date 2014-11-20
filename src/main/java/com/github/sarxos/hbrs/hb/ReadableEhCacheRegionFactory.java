package com.github.sarxos.hbrs.hb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.hibernate.EhCacheRegionFactory;

import org.hibernate.cache.CacheException;
import org.hibernate.cfg.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is cache region factory for Hibernate driver database models. The configuration file is
 * located in schema directory for this specific table. The class has been created on the base of
 * code from {@link EhCacheRegionFactory}, so in case of any questions and/or issues one needs to
 * check out this class first to understand how it works.
 *
 * @author Bartosz Firyn (sarxos)
 */
public abstract class ReadableEhCacheRegionFactory extends EhCacheRegionFactory {

	/**
	 * I'm the logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(ReadableEhCacheRegionFactory.class);

	public ReadableEhCacheRegionFactory(Properties prop) {
		super(prop);
	}

	/**
	 * Return stream from the EhCache configuration resource. Don't worry, the stream will always be
	 * closed in the parent class.
	 *
	 * @param resource the resource name
	 * @return Input stream to resource
	 * @throws IOException when resource cannot be open
	 */
	protected abstract InputStream readEhCacheConfiguration(String resource) throws IOException;

	@Override
	public void start(Settings settings, Properties properties) throws CacheException {

		this.settings = settings;

		// if manager has already been created

		if (manager != null) {
			LOG.error("Attempt to restart an already started EhCacheRegionFactory");
			return;
		}

		// get resource file name (cache configuration) from Hibernate properties

		String resource = null;
		String name = null;

		if (properties != null) {

			// read cache configuration file location from hibernate configuration properties

			resource = (String) properties.get(NET_SF_EHCACHE_CONFIGURATION_RESOURCE_NAME);
			if (resource == null) {
				throw new RuntimeException("Hibernate config is missing " + NET_SF_EHCACHE_CONFIGURATION_RESOURCE_NAME);
			}

			// get the cache manager name from hibernate configuration properties

			name = properties.getProperty(NET_SF_EHCACHE_CACHE_MANAGER_NAME);
		}

		try (InputStream is = readEhCacheConfiguration(resource)) {

			Configuration configuration = ConfigurationFactory.parseConfiguration(is);

			// override cache manager name if it's set in hibernate cfg
			if (name != null) {
				LOG.debug("Overwriting cache manager name to {}", name);
				configuration.setName(name);
			}

			// create cache manager and register it as mbean

			manager = new CacheManager(configuration);
			mbeanRegistrationHelper.registerMBean(manager, properties);

			LOG.debug("Cache manager has been created");

		} catch (net.sf.ehcache.CacheException e) {

			// some error handling, this is taken from EhCacheRegionFactory class, for more details
			// please check its source code

			if (e.getMessage().startsWith("Cannot parseConfiguration CacheManager. Attempt to create a new instance of CacheManager using the diskStorePath")) {
				throw new CacheException("Attempt to restart an already started EhCacheRegionFactory.", e);
			} else {
				throw new CacheException(e);
			}

		} catch (IOException e) {
			throw new CacheException(e);
		}
	}
}
