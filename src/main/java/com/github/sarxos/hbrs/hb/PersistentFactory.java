package com.github.sarxos.hbrs.hb;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.cfg.AnnotationConfiguration;


/**
 * The annotation used on persistence keeper types to specify which file should be used to create
 * session factory for given type.
 *
 * @author Bartosz Firyn (sarxos)
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface PersistentFactory {

	/**
	 * Hibernate configuration file path.
	 *
	 * @return Hibernate configuration file path
	 */
	String path() default "hibernate.cfg.xml";

	/**
	 * Hibernate configuration file path resolver.
	 *
	 * @return Path resolver
	 */
	Class<? extends PersistenceFactoryPathResolver> resolver() default PersistenceFactoryPathResolver.class;

	/**
	 * Hibernate configuration class to be used.
	 *
	 * @return Hibernate configuration class
	 */
	Class<? extends AnnotationConfiguration> configuration() default AnnotationConfiguration.class;
}
