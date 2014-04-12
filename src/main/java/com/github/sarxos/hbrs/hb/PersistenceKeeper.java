package com.github.sarxos.hbrs.hb;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import org.glassfish.jersey.process.internal.RequestScoped;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.metadata.ClassMetadata;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
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
public abstract class PersistenceKeeper implements Closeable {

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
	private static final Logger LOG = LoggerFactory.getLogger(PersistenceKeeper.class);

	/**
	 * Bean validator (JSR 349).
	 */
	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/**
	 * Hibernate session factory.
	 */
	private static SessionFactory factory = buildSessionFactory();

	private static List<Class<?>> daoClasses = null;

	private static int batchSize = 50;

	/**
	 * Hibernate session.
	 */
	private Session sessions;

	private StatelessSession statelessSession;

	public PersistenceKeeper() {
	}

	private AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Dispose keeper. This will flush and destroy Hibernate session. Please
	 * note that L1 Hibernate cache will not be affected by this operation.
	 */
	@Override
	public void close() {

		if (closed.compareAndSet(false, true)) {

			LOG.debug("Closing persistence keeper");

			if (sessions != null && sessions.isOpen()) {
				sessions.flush();
				sessions.close();
			}

			if (statelessSession != null) {
				statelessSession.close();
			}
		}
	}

	/**
	 * Create SessionFactory from hibernate.cfg.xml
	 * 
	 * @param packages the packages list where database entities are located
	 * @return Session factory
	 */
	private static SessionFactory buildSessionFactory() {

		AnnotationConfiguration ac = new AnnotationConfiguration()
			.configure();

		SessionFactory factory = null;
		try {

			for (Class<?> c : loadClasses()) {
				ac.addAnnotatedClass(c);
			}

			factory = ac.buildSessionFactory();

		} catch (Throwable e) {
			LOG.error("Initial SessionFactory creation failed", e);
			throw new ExceptionInInitializerError(e);
		}

		return factory;
	}

	/**
	 * @return Return Hibernate session factory
	 */
	public static final SessionFactory getSessionFactory() {
		if (factory == null) {
			throw new IllegalStateException("Hibernate has not been initialized");
		}
		return factory;
	}

	public static int getBatchSize() {
		return batchSize;
	}

	/**
	 * Initialize. This will build session factory.
	 */
	public static final void initialize() {
		factory = buildSessionFactory();
	}

	/**
	 * Shutdown persistence keeper. This operation will close session factory.
	 */
	public static void shutdown() {
		if (factory != null) {
			factory.close();
		}
	}

	/**
	 * @return Return current Hibernate session
	 */
	public Session session() {
		if (closed.get()) {
			throw new IllegalStateException("Keeper has been already closed");
		}
		if (sessions == null) {
			return sessions = getSessionFactory().openSession();
		} else {
			return sessions;
		}
	}

	/**
	 * @return Stateless session
	 */
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

	/**
	 * Will persist stateless (transient) entity. This method will validate
	 * entity against possible constraints violation.
	 * 
	 * @param stateless the transient entity to be persisted
	 * @return Return managed entity
	 */
	public <T extends Identity<?>> Collection<T> persist(Collection<T> entities) {
		return store(entities, CommitType.PERSIST);
	}

	public <T extends Identity<?>> Collection<T> save(Collection<T> entities) {
		return store(entities, CommitType.SAVE);
	}

	public <T extends Identity<?>> Collection<T> saveOrUpdate(Collection<T> entities) {
		return store(entities, CommitType.SAVE_OR_UPDATE);
	}

	/**
	 * Fetch entity from the database and return managed instance.
	 * 
	 * @param clazz the entity class to be fetched
	 * @param id the entity ID
	 * @return Return managed entity
	 */
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

	@SuppressWarnings("unchecked")
	public <T extends Identity<?>> T reget(T entity) {

		if (entity.getId() == null) {
			throw new IllegalStateException("Only persistent entities can be reget");
		}

		entity = (T) get(entity.getClass(), entity.getId());

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

	public <T> T refresh(T entity) {

		if (entity == null) {
			throw new IllegalArgumentException("Database entity cannot be null");
		}

		session().refresh(entity);

		PersistenceHooks.hook(entity, PostLoad.class);

		return entity;
	}

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

	/**
	 * Check if entity of given class and with the specified ID exists in the
	 * database.
	 * 
	 * @param clazz the entity class
	 * @param id the entity ID
	 * @return Return true if entity exists, false otherwise
	 */
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

	/**
	 * This method will return all instances of given entity. Be careful when
	 * using this method because there can be millions of records in the
	 * database, and thus, your memory consumption may gone wild.
	 * 
	 * @param clazz the entity class to be fetched
	 * @return Return list of managed objects
	 */
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

	/**
	 * Return paged result with specific entities inside.
	 * 
	 * @param clazz the entity class
	 * @param pgNum the first record offset
	 * @param pgSize the max number of records per page
	 * @return Paged result
	 */
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

	/**
	 * Will persist stateless (transient) entity. This method will validate
	 * entity against possible constraints violation.
	 * 
	 * @param stateless the transient entity to be persisted
	 * @return Return managed entity
	 */
	public <T extends Identity<?>> T persist(T stateless) {
		return store(stateless, CommitType.PERSIST);
	}

	public <T extends Identity<?>> T merge(T entity) {
		return store(entity, CommitType.MERGE);
	}

	public <T extends Identity<?>> T save(T entity) {
		return store(entity, CommitType.SAVE);
	}

	public <T extends Identity<?>> T saveOrUpdate(T entity) {
		return store(entity, CommitType.SAVE_OR_UPDATE);
	}

	/**
	 * Merge state of the detached instance into the corresponding managed
	 * instance in the database.
	 * 
	 * @param entity the detached or managed entity instance
	 * @return Return managed entity
	 */
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

		// bean validation

		validate(entity);

		// hooks

		if (type == CommitType.PERSIST) {
			PersistenceHooks.hook(entity, PrePersist.class);
		} else {
			PersistenceHooks.hook(entity, PreUpdate.class);
		}

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

	public <T extends Identity<?>> Collection<T> update(Collection<T> entities) {
		return store(entities, CommitType.UPDATE);
	}

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

			validate(entity);

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
		}

		if (entities.size() >= batchSize) {
			s = factory.openSession();
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

	/**
	 * This method takes dry object taken directly from the REST layer and
	 * hydrate it using the data from the database. It takes all fields visible
	 * in the REST interface and populate every one of them with the database
	 * column value if and only if given field in dry object is null.
	 * 
	 * @param dry the dry REST object to be hydrated
	 * @return Return hydrated object
	 */
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

	/**
	 * This method takes dry object taken directly from the REST layer and
	 * hydrate it using the data from the managed entity. It takes all fields
	 * visible in the REST interface and populate every one of them with the
	 * managed entity corresponding attribute value if and only if given field
	 * in dry object is null.
	 * 
	 * @param dry the dry REST object to be hydrated
	 * @param manbaed the corresponding managed entity from the DB
	 * @return Return hydrated object
	 */
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

	/**
	 * Delete given entity.
	 * 
	 * @param entity the entity to be removed
	 * @return Return detached entity without the ID
	 */
	public <T> T delete(T entity) {

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

	/**
	 * Delete entity of given class with given ID.
	 * 
	 * @param clazz the entity class
	 * @param id the entity ID
	 * @return Return true if entity was removed, false otherwise
	 */
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

	public <T> T evict(T entity) {
		session().evict(entity);
		return entity;
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

	private static Class<?>[] loadClasses() throws ParserConfigurationException, SAXException, IOException, XPathException {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false);
		dbf.setNamespaceAware(true);
		dbf.setFeature("http://xml.org/sax/features/namespaces", false);
		dbf.setFeature("http://xml.org/sax/features/validation", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		DocumentBuilder builder = dbf.newDocumentBuilder();
		Document doc = builder.parse(PersistenceKeeper.class.getResourceAsStream("/hibernate.cfg.xml"));
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		String modelPackages = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='com.github.sarxos.hbrs.db.model']/text()").evaluate(doc, XPathConstants.STRING);

		if (modelPackages == null) {
			throw new IllegalStateException("Property 'com.github.sarxos.jaxrshb.db.packages' has not been defined in hibernate.cfg.xml file");
		}

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

		return classes.toArray(new Class<?>[classes.size()]);
	}

	private static final List<Class<?>> getDaoClasses0() throws ParserConfigurationException, SAXException, IOException, XPathException {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false);
		dbf.setNamespaceAware(true);
		dbf.setFeature("http://xml.org/sax/features/namespaces", false);
		dbf.setFeature("http://xml.org/sax/features/validation", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		DocumentBuilder builder = dbf.newDocumentBuilder();
		Document doc = builder.parse(PersistenceKeeper.class.getResourceAsStream("/hibernate.cfg.xml"));
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();

		String batchSizeStr = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='hibernate.jdbc.batch_size']/text()").evaluate(doc, XPathConstants.STRING);
		if (batchSizeStr != null && !batchSizeStr.isEmpty()) {
			batchSize = Integer.parseInt(batchSizeStr);
		}

		String daoPackage = (String) xpath.compile("/hibernate-configuration/session-factory/property[@name='com.github.sarxos.hbrs.db.dao']/text()").evaluate(doc, XPathConstants.STRING);

		if (daoPackage == null || daoPackage.trim().isEmpty()) {
			throw new IllegalStateException("Database DAO package must be provided");
		}

		List<Class<?>> daoClasses = new ArrayList<Class<?>>();
		String[] splitted = daoPackage.split(",");

		for (String p : splitted) {
			if (!(p = p.trim()).isEmpty()) {
				daoClasses.addAll(new Reflections(p).getSubTypesOf(PersistenceKeeper.class));
			}
		}

		if (LOG.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder("The following database DAO classes has been found:");
			for (Class<?> c : daoClasses) {
				sb.append("\n  ").append(c);
			}
			LOG.info(sb.toString());
		}

		return daoClasses;
	}

	public static List<Class<?>> getDaoClasses() {
		if (daoClasses == null) {
			try {
				daoClasses = getDaoClasses0();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return daoClasses;
	}

	/**
	 * Initialize all lazy-loaded first-level entities which are not
	 * JSON-ignored.
	 * 
	 * @param entity the entity from which fields will be lazy loaded
	 * @return Return the same object with lazy fields initialized
	 */
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
