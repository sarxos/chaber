package com.github.sarxos.hbrs.hb;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.ManagedBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathException;

import org.glassfish.jersey.process.internal.RequestScoped;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.metadata.ClassMetadata;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This class provide generic access to the Hibernate persistence layer. It's
 * very important to close persistence keeper when it's no longer necessary.
 *
 * @author Bartosz Firyn (bfiryn)
 */
@RequestScoped
public abstract class PersistenceKeeperImpl implements Closeable, PersistenceKeeper {

	private static enum CommitType {

		SAVE,

		UPDATE,

		SAVE_OR_UPDATE,

		MERGE,

		PERSIST,
	}

	/**
	 * Logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(PersistenceKeeperImpl.class);

	/**
	 * Bean validator (JSR 349).
	 */
	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/**
	 * Hibernate session factory.
	 */
	private static final ConcurrentHashMap<String, SessionFactory> FACTORIES = new ConcurrentHashMap<String, SessionFactory>();

	/**
	 * Keeper class to hibernate session file path mapping.
	 */
	private static final Map<Class<? extends PersistenceKeeper>, String> PATHS = new HashMap<>();

	/**
	 * Keeper class to hibernate configuration class mapping.
	 */
	private static final Map<Class<? extends PersistenceKeeper>, Class<? extends AnnotationConfiguration>> CONFIGS = new HashMap<>();

	/**
	 * Batch size.
	 */
	private static int batchSize = 50;

	/**
	 * Stateful session.
	 */
	protected Session session;

	/**
	 * Stateless session.
	 */
	protected StatelessSession statelessSession;

	/**
	 * Is closed.
	 */
	protected AtomicBoolean closed = new AtomicBoolean();

	public PersistenceKeeperImpl() {
	}

	@Override
	public void close() {

		if (closed.compareAndSet(false, true)) {

			LOG.debug("Closing {} persistence keeper", getClass());

			if (session != null && session.isOpen()) {
				session.flush();
				session.close();
			}

			if (statelessSession != null) {
				statelessSession.close();
			}
		}
	}

	/**
	 * Create SessionFactory from hibernate.cfg.xml
	 *
	 * @return Session factory
	 */
	protected static SessionFactory buildSessionFactory() {
		return buildSessionFactory(null, (File) null);
	}

	protected static SessionFactory buildSessionFactory(Class<? extends AnnotationConfiguration> clazz, String path) {

		AnnotationConfiguration config;
		try {
			config = clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalStateException("System was unable to instantiate " + clazz, e);
		}

		File file = new File(path);

		return buildSessionFactory(config, file);
	}

	/**
	 * Create SessionFactory from hibernate.cfg.xml
	 *
	 * @param file the file with hibernate configuration
	 * @return Session factory
	 */
	protected static SessionFactory buildSessionFactory(AnnotationConfiguration configuration, File file) {

		if (file == null) {
			configuration = configuration.configure();
		} else {
			configuration = configuration.configure(file);
		}

		try {

			for (Class<?> c : loadClasses(configuration)) {

				LOG.debug("Adding annotated {} to factory configuration from {}", c, file);

				configuration.addAnnotatedClass(c);
			}

			return configuration.buildSessionFactory();

		} catch (Exception e) {

			LOG.error("Initial SessionFactory creation failed", e);

			throw new ExceptionInInitializerError(e);
		}
	}

	@Override
	public String getSessionFactoryPath() {
		return getSessionFactoryPath(getClass());
	}

	/**
	 * Get hibernate configuration class for specific persistence keeper.
	 *
	 * @param clazz the persistent keeper class
	 * @return Return Hibernate configuration class
	 */
	public static Class<? extends AnnotationConfiguration> getHibernateConfigurationClass(Class<? extends PersistenceKeeper> clazz) {

		Class<? extends AnnotationConfiguration> cfg = CONFIGS.get(clazz);
		if (cfg != null) {
			return cfg;
		}

		PersistentFactory pf = clazz.getAnnotation(PersistentFactory.class);
		if (pf == null) {
			return null;
		}

		return pf.configuration();
	}

	/**
	 * Get session factory path for specific persistence keeper.
	 *
	 * @param clazz the persistent keeper class
	 * @return Return Hibernate session factory
	 */
	public static String getSessionFactoryPath(Class<? extends PersistenceKeeper> clazz) {

		String path = PATHS.get(clazz);
		if (path != null) {
			return path;
		}

		PersistentFactory pf = clazz.getAnnotation(PersistentFactory.class);
		if (pf == null) {
			throw new IllegalStateException("The persistent factory path is missing on " + clazz);
		}

		path = pf.path();

		String resolved = null;

		Class<? extends PersistenceFactoryPathResolver> resolverClass = pf.resolver();
		if (resolverClass == PersistenceFactoryPathResolver.class) {
			resolved = path;
		} else {

			ManagedBean mb = resolverClass.getAnnotation(ManagedBean.class);
			if (mb != null) {

				String name = mb.value();
				if (name.isEmpty()) {
					name = clazz.getName();
				}

				MBeanServer server = ManagementFactory.getPlatformMBeanServer();

				String fname = resolverClass.getPackage().getName() + ":name=" + name;

				try {
					ObjectName oname = new ObjectName(fname);
					resolved = (String) server.invoke(oname, "resolve", new Object[] { path }, new String[] { "java.lang.String" });
				} catch (MalformedObjectNameException | InstanceNotFoundException | ReflectionException
					| MBeanException e) {
					LOG.trace("Managed bean exception", e);
					LOG.debug("Cannot resolve path using managed bean, will create new instance");
				}
			}

			if (resolved == null) {
				try {
					resolved = resolverClass.newInstance().resolve(path);
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}

		PATHS.put(clazz, resolved);

		LOG.debug("Session factory path for {} is {}", clazz, resolved);

		return resolved;
	}

	/**
	 * Get session factory for specific persistence keeper.
	 *
	 * @param clazz the persistent keeper class
	 * @return Return Hibernate session factory
	 */
	public static SessionFactory getSessionFactory(Class<? extends PersistenceKeeper> clazz) {

		String path = getSessionFactoryPath(clazz);
		Class<? extends AnnotationConfiguration> config = getHibernateConfigurationClass(clazz);

		return getSessionFactory(config, path);
	}

	/**
	 * Get session factory from specific file path.
	 *
	 * @param clazz the hibernate configuration class
	 * @param path the path to session factory file
	 * @return Return Hibernate session factory
	 */
	public static SessionFactory getSessionFactory(Class<? extends AnnotationConfiguration> clazz, String path) {

		SessionFactory factory = null;
		SessionFactory prev = null;

		while ((factory = FACTORIES.get(path)) == null) {

			synchronized (PersistenceKeeperImpl.class) {
				if ((factory = FACTORIES.get(path)) == null) {
					factory = buildSessionFactory(clazz, path);
					prev = FACTORIES.putIfAbsent(path, factory);
				}
			}

			if (prev != null) {
				LOG.debug("Concurrent session factory creation detected for {}, closing new one", path);
			} else {
				LOG.debug("New session factory has been created for {}", path);
			}

			if (prev != null) {
				factory.close();
				factory = prev;
			}
		}

		return factory;
	}

	@Override
	public SessionFactory getSessionFactory() {

		String path = getSessionFactoryPath();
		Class<? extends AnnotationConfiguration> config = getHibernateConfigurationClass(getClass());

		return getSessionFactory(config, path);
	}

	public static int getBatchSize() {
		return batchSize;
	}

	/**
	 * Shutdown persistence keeper. This operation will close session factory.
	 */
	public static void shutdown() {
		for (SessionFactory factory : FACTORIES.values()) {
			factory.close();
		}
		FACTORIES.clear();
	}

	@Override
	public Session session() {
		if (closed.get()) {
			throw new IllegalStateException("Keeper has been already closed");
		}
		if (session == null) {
			return session = getSessionFactory().openSession();
		} else {
			return session;
		}
	}

	@Override
	public StatelessSession stateless() {
		if (closed.get()) {
			throw new IllegalStateException("Keeper has been already closed");
		}
		if (statelessSession == null) {
			statelessSession = getSessionFactory().openStatelessSession();
		}
		return statelessSession;
	}

	private <T> boolean isEntity(Class<T> c) {
		Class<?> cc = c;
		if (cc.getAnnotation(Entity.class) == null) {
			return (cc = cc.getSuperclass()) == null ? false : isEntity(cc);
		}
		return true;
	}

	@Override
	public <T extends Identity<?>> Collection<T> persist(Collection<T> entities) {
		return store(entities, CommitType.PERSIST);
	}

	@Override
	public <T extends Identity<?>> Collection<T> save(Collection<T> entities) {
		return store(entities, CommitType.SAVE);
	}

	@Override
	public <T extends Identity<?>> Collection<T> saveOrUpdate(Collection<T> entities) {
		return store(entities, CommitType.SAVE_OR_UPDATE);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> clazz, Serializable id) {

		if (clazz == null) {
			throw new IllegalArgumentException("Database entity class cannot be null");
		}
		if (id == null) {
			throw new IllegalArgumentException("Database entity ID cannot be null");
		}

		T entity = (T) session().get(clazz, id);

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Identity<?>> T reget(T entity) {

		if (entity.getId() == null) {
			throw new IllegalStateException("Only persistent entities can be reget");
		}

		entity = (T) get(entity.getClass(), entity.getId());

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

	@Override
	public <T> T refresh(T entity) {

		if (entity == null) {
			throw new IllegalArgumentException("Database entity cannot be null");
		}

		session().refresh(entity);

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

	@Override
	public <T> int count(Class<T> clazz) {

		if (clazz == null) {
			throw new IllegalArgumentException("Database entity class cannot be null");
		}
		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}

		long count = (Long) session()
			.createQuery(String.format("select count(1) from %s", clazz.getSimpleName()))
			.uniqueResult();

		return (int) count;
	}

	@Override
	public <T extends Identity<?>> boolean exists(Class<T> clazz, Serializable id) {

		if (clazz == null) {
			throw new IllegalArgumentException("Database entity class cannot be null");
		}
		if (id == null) {
			throw new IllegalArgumentException("Entity ID cannot benull");
		}
		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}

		return session().get(clazz, id) != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> list(Class<T> clazz) {

		if (clazz == null) {
			throw new IllegalArgumentException("Database entity class cannot be null");
		}
		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}

		List<T> entities = session()
			.createQuery(String.format("from %s", clazz.getSimpleName()))
			.setCacheable(false)
			.list();

		for (T entity : entities) {
			PersistenceHooks.hook(entity, PostLoad.class);
		}

		return entities;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> list(Class<T> clazz, int pgNum, int pgSize) {

		if (clazz == null) {
			throw new IllegalArgumentException("Database entity class cannot be null");
		}
		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}
		if (pgNum < 0) {
			throw new IllegalArgumentException("Offset cannot be negative");
		}
		if (pgSize <= 0) {
			throw new IllegalArgumentException("Max records count must be positive");
		}

		List<T> entities = session()
			.createQuery(String.format("from %s", clazz.getSimpleName()))
			.setFirstResult(pgNum * pgSize)
			.setMaxResults(pgSize)
			.setCacheable(false)
			.list();

		for (T entity : entities) {
			PersistenceHooks.hook(entity, PostLoad.class);
		}

		return entities;
	}

	@Override
	public <T extends Identity<?>> ScrollableResultsIterator<T> cursor(Class<T> clazz) {

		Session session = session();
		ScrollableResults scroll = session
			.createQuery(String.format("from %s", clazz.getSimpleName()))
			.setReadOnly(true)
			.setFetchSize(Integer.MIN_VALUE)
			.scroll(ScrollMode.FORWARD_ONLY);

		return new ScrollableResultsIterator<>(scroll, session);
	}

	@Override
	public <T extends Identity<?>> T persist(T stateless) {
		return store(stateless, CommitType.PERSIST);
	}

	@Override
	public <T extends Identity<?>> T merge(T entity) {
		return store(entity, CommitType.MERGE);
	}

	@Override
	public <T extends Identity<?>> T save(T entity) {
		return store(entity, CommitType.SAVE);
	}

	@Override
	public <T extends Identity<?>> T saveOrUpdate(T entity) {
		return store(entity, CommitType.SAVE_OR_UPDATE);
	}

	@Override
	public <T extends Identity<?>> T update(T entity) {
		return store(entity, CommitType.UPDATE);
	}

	@SuppressWarnings("unchecked")
	private <T extends Identity<?>> T store(T entity, CommitType type) {

		if (entity == null) {
			throw new IllegalArgumentException("Persistent object to be updated cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("Commit type cannot be null");
		}

		Class<?> clazz = entity.getClass();

		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}

		switch (type) {
			case UPDATE:
			case MERGE:
				if (entity.getId() == null) {
					throw new IllegalStateException("Persistent identity to be updated/merged must have ID set");
				}
				break;
			case PERSIST:
				if (entity.getId() != null) {
					throw new IllegalStateException("Stateless identity to be persist must not have ID set");
				}
				break;
			case SAVE:
			case SAVE_OR_UPDATE:
				break;
		}

		// hooks

		if (type == CommitType.PERSIST || type == CommitType.SAVE) {
			PersistenceHooks.hook(entity, PrePersist.class);
		} else {
			PersistenceHooks.hook(entity, PreUpdate.class);
		}

		// bean validation

		validate(entity);

		Session s = session();
		Transaction t = s.beginTransaction();
		HibernateException he = null;

		try {
			switch (type) {
				case UPDATE:
					s.update(entity);
					break;
				case MERGE:
					entity = (T) s.merge(entity);
					break;
				case PERSIST:
					s.persist(entity);
					break;
				case SAVE:
					s.save(entity);
					break;
				case SAVE_OR_UPDATE:
					s.saveOrUpdate(entity);
					break;
				default:
					throw new RuntimeException("Not supported, yet");
			}

			t.commit();

		} catch (HibernateException e) {
			throw he = e;
		} finally {
			if (he != null) {
				try {
					t.rollback();
				} catch (Exception e) {
					LOG.error("Cannot rollback", e);
				}
			}
		}

		if (type == CommitType.PERSIST) {
			PersistenceHooks.hook(entity, PostPersist.class);
		} else {
			PersistenceHooks.hook(entity, PostUpdate.class);
		}

		return entity;
	}

	@Override
	public <T extends Identity<?>> Collection<T> update(Collection<T> entities) {
		return store(entities, CommitType.UPDATE);
	}

	@Override
	public <T extends Identity<?>> Collection<T> merge(Collection<T> entities) {
		return store(entities, CommitType.MERGE);
	}

	private <T extends Identity<?>> Collection<T> store(Collection<T> entities, CommitType type) {

		if (entities.isEmpty()) {
			return entities;
		}

		Class<?> clazz = null;
		Session s = null;

		for (T entity : entities) {

			// TODO: move to mapping

			if (!isEntity(clazz = entity.getClass())) {
				throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
			}

			// for update commits we have to verify if ID is set

			switch (type) {
				case UPDATE:
				case MERGE:
					if (entity.getId() == null) {
						throw new IllegalStateException("Persistent identity to be stored must have ID set");
					}
					break;
				default:
					break;
			}

			// persistence hooks

			switch (type) {
				case PERSIST:
				case SAVE:
					PersistenceHooks.hook(entity, PrePersist.class);
					break;
				case UPDATE:
				case MERGE:
					PersistenceHooks.hook(entity, PreUpdate.class);
					break;
				case SAVE_OR_UPDATE:
					PersistenceHooks.hook(entity, PrePersist.class);
					PersistenceHooks.hook(entity, PreUpdate.class);
					break;
			}

			// bean validation

			validate(entity);
		}

		if (entities.size() >= batchSize) {
			s = FACTORIES.get(getSessionFactoryPath()).openSession();
		} else {
			s = session();
		}

		Transaction t = s.beginTransaction();
		HibernateException he = null;

		try {

			int i = 0;
			for (T entity : entities) {

				switch (type) {
					case PERSIST:
						s.persist(entity);
						break;
					case SAVE:
						s.save(entity);
						break;
					case UPDATE:
						s.update(entity);
						break;
					case MERGE:
						s.merge(entity);
						break;
					case SAVE_OR_UPDATE:
						s.saveOrUpdate(entity);
						break;
				}

				// batch mode

				if (s != session()) {
					if (i++ > 0 && i % batchSize == 0) {
						s.flush();
						s.clear();
					}
				}
			}

			t.commit();

		} catch (HibernateException e) {
			throw he = e;
		} finally {

			if (he != null) {
				try {
					t.rollback();
				} catch (Exception e) {
					LOG.error("Cannot rollback", e);
				}
			}

			// in case of batch mode

			if (s != session()) {

				if (he == null) {
					s.flush();
				}

				s.clear();
				s.close();
			}
		}

		for (T entity : entities) {
			if (type == CommitType.PERSIST) {
				PersistenceHooks.hook(entity, PostPersist.class);
			} else {
				PersistenceHooks.hook(entity, PostUpdate.class);
			}
		}

		return entities;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Identity<?>> T hydrate(T dry) {

		if (dry == null) {
			throw new IllegalArgumentException("Dry entity to be hydrated must not be null");
		}
		if (dry.getId() == null) {
			throw new IllegalStateException("Only persistent entities can be hydrated");
		}

		Class<?> clazz = dry.getClass();

		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not a database entity", clazz.getName()));
		}

		Serializable id = ((Identity<?>) dry).getId();
		T managed = (T) get(clazz, id);

		for (Field f : clazz.getDeclaredFields()) {

			if (!f.isAccessible()) {
				f.setAccessible(true);
			}

			Annotation jp = f.getAnnotation(JsonProperty.class);
			Annotation c = f.getAnnotation(Column.class);

			if (jp != null && c != null) {
				Object o = null;
				try {
					if ((o = f.get(dry)) != null) {
						f.set(managed, o);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		return managed;
	}

	@Override
	public <T> T hydrate(T dry, T managed) {

		if (dry == null) {
			throw new IllegalArgumentException("Dry entity to be hydrated must not be null");
		}
		if (managed == null) {
			throw new IllegalArgumentException("Managed entity to be hydrated must not be null");
		}

		if (dry instanceof Identity) {
			if (((Identity<?>) dry).getId() == null) {
				throw new IllegalStateException("Only persistent entities can be hydrated");
			}
		} else {
			throw new IllegalArgumentException("Dry entity must be an identity");
		}

		for (Field f : dry.getClass().getDeclaredFields()) {

			if (!f.isAccessible()) {
				f.setAccessible(true);
			}

			Annotation jp = f.getAnnotation(JsonProperty.class);
			Annotation c = f.getAnnotation(Column.class);

			if (jp != null && c != null) {

				Object o = null;
				try {
					if ((o = f.get(dry)) != null) {
						f.set(managed, o);
					}
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}

		return managed;
	}

	@Override
	public <T extends Identity<?>> T delete(T entity) {

		if (entity == null) {
			throw new IllegalArgumentException("Persistent object to be deleted cannot be null");
		}

		PersistenceHooks.hook(entity, PreRemove.class);

		Session s = session();
		Transaction t = s.beginTransaction();
		HibernateException he = null;

		try {
			s.delete(entity);
			t.commit();
		} catch (HibernateException e) {
			throw he = e;
		} finally {
			if (he != null) {
				try {
					t.rollback();
				} catch (Exception e) {
					LOG.error("Cannot rollback", e);
				}
			}
		}

		PersistenceHooks.hook(entity, PostRemove.class);

		// detached identities must have ID set to null - this is very helpful
		// trick which bind entity with the state which can be resolved without
		// using Hibernate (e.g. in upper presentation layers)

		if (entity instanceof Identity) {
			((Identity<?>) entity).setId(null);
		}

		return evict(entity);
	}

	@Override
	public <T extends Identity<?>> Collection<T> delete(Collection<T> entities) {

		if (entities.isEmpty()) {
			return entities;
		}

		for (T entity : entities) {
			PersistenceHooks.hook(entity, PreRemove.class);
		}

		Session s = session();
		Transaction t = s.beginTransaction();
		HibernateException he = null;

		try {
			for (T entity : entities) {
				s.delete(entity);
			}
			t.commit();
		} catch (HibernateException e) {
			throw he = e;
		} finally {
			if (he != null) {
				try {
					t.rollback();
				} catch (Exception e) {
					LOG.error("Cannot rollback", e);
				}
			}
		}

		for (T entity : entities) {
			PersistenceHooks.hook(entity, PostRemove.class);
		}

		// detached identities must have ID set to null - this is very helpful
		// trick which bind entity with the state which can be resolved without
		// using Hibernate (e.g. in upper presentation layers)

		for (T entity : entities) {
			entity.setId(null);
			evict(entity);
		}

		return entities;

	}

	@Override
	public <T extends Identity<?>> boolean delete(Class<T> clazz, Serializable id) {

		if (clazz == null) {
			throw new IllegalArgumentException("Entity class cannot be null");
		}
		if (id == null) {
			throw new IllegalArgumentException("Entity ID cannot be null");
		}

		ClassMetadata cm = getSessionFactory().getClassMetadata(clazz);

		String idn = cm.getIdentifierPropertyName();
		String csn = clazz.getSimpleName();

		Session s = session();
		Query q = s
			.createQuery(String.format("delete from %s e where e.%s = :id", csn, idn))
			.setSerializable("id", id)
			.setCacheable(false);

		HibernateException he = null;
		Transaction t = s.beginTransaction();

		int count = -1;
		try {
			count = q.executeUpdate();
			t.commit();
		} catch (HibernateException e) {
			throw he = e;
		} finally {
			if (he != null) {
				try {
					t.rollback();
				} catch (Exception e) {
					LOG.error("Cannot rollback", e);
				}
			}
		}

		return count > 0;
	}

	@Override
	public <T extends Identity<?>> T evict(T entity) {
		session().evict(entity);
		return entity;
	}

	@Override
	public <T extends Identity<?>> Collection<T> evict(Collection<T> entities) {
		for (T entity : entities) {
			evict(entity);
		}
		return entities;
	}

	/**
	 * Validate entity against constraints violation.
	 *
	 * @param entity the entity to be validated
	 * @throws EntityValidationException when entity is not valid
	 */
	protected static void validate(Object entity) {

		Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(entity);
		if (violations.isEmpty()) {
			return;
		}

		throw new EntityValidationException(entity, violations);
	}

	private static Class<?>[] loadClasses(Configuration configuration) throws ParserConfigurationException, SAXException, IOException, XPathException {

		String modelPackages = configuration.getProperty("com.github.sarxos.hbrs.db.model");
		if (modelPackages == null) {
			throw new IllegalStateException("Property 'com.github.sarxos.hbrs.db.model' has not been defined in hibernate configuration file");
		}

		LOG.debug("The com.github.sarxos.hbrs.db.model is {}", modelPackages);

		List<Class<?>> classes = new ArrayList<Class<?>>();
		String[] splitted = modelPackages.split(",");

		for (String p : splitted) {
			if (!(p = p.trim()).isEmpty()) {
				classes.addAll(new Reflections(p).getTypesAnnotatedWith(Entity.class));
			}
		}

		if (LOG.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder("The following entity classes has been found:");
			for (Class<?> c : classes) {
				sb.append("\n  ").append(c);
			}
			LOG.info(sb.toString());
		}

		String batchSizeStr = configuration.getProperty("hibernate.jdbc.batch_size");
		if (batchSizeStr != null && !batchSizeStr.isEmpty()) {
			batchSize = Integer.parseInt(batchSizeStr);
		}

		return classes.toArray(new Class<?>[classes.size()]);
	}

	@Override
	public <T> T lazyload(T entity) {

		if (entity == null) {
			throw new IllegalArgumentException("REST entity cannot be null");
		}

		for (Field f : entity.getClass().getDeclaredFields()) {

			if (!f.isAccessible()) {
				f.setAccessible(true);
			}

			Annotation ji = f.getAnnotation(JsonIgnore.class);
			if (ji != null) {
				continue;
			}

			Annotation mto = f.getAnnotation(ManyToOne.class);
			Annotation otm = f.getAnnotation(OneToMany.class);
			Annotation mtm = f.getAnnotation(ManyToMany.class);

			if (mto != null || otm != null || mtm != null) {
				try {
					Object o = f.get(entity);
					if (!Hibernate.isInitialized(o)) {
						Hibernate.initialize(o);
					}
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}

		return entity;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Identity<?>> T load(Class<T> clazz, Serializable id) {

		if (!isEntity(clazz)) {
			throw new IllegalArgumentException(String.format("Class %s is not an identity", clazz.getName()));
		}

		T entity = (T) session().load(clazz, id);

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

	public static final Collection<Serializable> identities(Collection<? extends Identity<? extends Serializable>> identities) {
		List<Serializable> ids = new ArrayList<Serializable>();
		for (Identity<? extends Serializable> identity : identities) {
			ids.add(identity.getId());
		}
		return ids;
	}
}
