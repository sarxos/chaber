package com.github.sarxos.hbrs.hb;

import javax.management.MXBean;


/**
 * Interface used in conjunction with {@link PersistentFactory} annotation to resolve hibernate
 * configuration file path in case it's dynamic.
 *
 * @author Bartosz Firyn (sarxos)
 */
@MXBean
public interface PersistenceFactoryPathResolver {

	/**
	 * Resolve hibernate configuration file path.
	 *
	 * @param path the
	 * @return Resolved path
	 */
	String resolve(String path);

}
