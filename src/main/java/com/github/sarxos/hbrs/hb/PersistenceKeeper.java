package com.github.sarxos.hbrs.hb;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;


public interface PersistenceKeeper extends Closeable {

	/**
	 * Dispose keeper. This will flush and destroy Hibernate session. Please note that L1 Hibernate
	 * cache will not be affected by this operation.
	 */
	@Override
	void close();

	/**
	 * Get local session factory path (for this persistence keeper).
	 *
	 * @return Local session factory path.
	 */
	String getSessionFactoryPath();

	/**
	 * @return Return Hibernate session factory
	 */
	SessionFactory getSessionFactory();

	/**
	 * @return Return current Hibernate session
	 */
	Session session();

	/**
	 * @return Stateless session
	 */
	StatelessSession stateless();

	/**
	 * Will persist stateless (transient) entity. This method will validate entity against possible
	 * constraints violation.
	 *
	 * @param <T> identity class
	 * @param entities the transient entities to be persisted
	 * @return Return managed entity
	 */
	<T extends Identity<?>> Collection<T> persist(Collection<T> entities);

	<T extends Identity<?>> Collection<T> save(Collection<T> entities);

	<T extends Identity<?>> Collection<T> saveOrUpdate(Collection<T> entities);

	/**
	 * Fetch entity from the database and return managed instance.
	 *
	 * @param <T> identity class
	 * @param clazz the entity class to be fetched
	 * @param id the entity ID
	 * @return Return managed entity
	 */
	<T> T get(Class<T> clazz, Serializable id);

	<T extends Identity<?>> T reget(T entity);

	<T> T refresh(T entity);

	<T> int count(Class<T> clazz);

	/**
	 * Check if entity of given class and with the specified ID exists in the database.
	 *
	 * @param <T> identity class
	 * @param clazz the entity class
	 * @param id the entity ID
	 * @return Return true if entity exists, false otherwise
	 */
	<T extends Identity<?>> boolean exists(Class<T> clazz, Serializable id);

	/**
	 * This method will return all instances of given entity. Be careful when using this method
	 * because there can be millions of records in the database, and thus, your memory consumption
	 * may gone wild.
	 *
	 * @param <T> identity class
	 * @param clazz the entity class to be fetched
	 * @return Return list of managed objects
	 */
	<T> List<T> list(Class<T> clazz);

	/**
	 * Return paged result with specific entities inside.
	 *
	 * @param <T> identity class
	 * @param clazz the entity class
	 * @param pgNum the first record offset
	 * @param pgSize the max number of records per page
	 * @return Paged result
	 */
	<T> List<T> list(Class<T> clazz, int pgNum, int pgSize);

	<T extends Identity<?>> ScrollableResultsIterator<T> cursor(Class<T> clazz);

	/**
	 * Will persist stateless (transient) entity. This method will validate entity against possible
	 * constraints violation.
	 *
	 * @param <T> identity class
	 * @param stateless the transient entity to be persisted
	 * @return Return managed entity
	 */
	<T extends Identity<?>> T persist(T stateless);

	<T extends Identity<?>> T merge(T entity);

	<T extends Identity<?>> T save(T entity);

	<T extends Identity<?>> T saveOrUpdate(T entity);

	/**
	 * Merge state of the detached instance into the corresponding managed instance in the database.
	 *
	 * @param <T> identity class
	 * @param entity the detached or managed entity instance
	 * @return Return managed entity
	 */
	<T extends Identity<?>> T update(T entity);

	<T extends Identity<?>> Collection<T> update(Collection<T> entities);

	<T extends Identity<?>> Collection<T> merge(Collection<T> entities);

	/**
	 * This method takes dry object taken directly from the REST layer and hydrate it using the data
	 * from the database. It takes all fields visible in the REST interface and populate every one
	 * of them with the database column value if and only if given field in dry object is null.
	 *
	 * @param <T> identity class
	 * @param dry the dry REST object to be hydrated
	 * @return Return hydrated object
	 */
	<T extends Identity<?>> T hydrate(T dry);

	/**
	 * This method takes dry object taken directly from the REST layer and hydrate it using the data
	 * from the managed entity. It takes all fields visible in the REST interface and populate every
	 * one of them with the managed entity corresponding attribute value if and only if given field
	 * in dry object is null.
	 *
	 * @param <T> identity class
	 * @param dry the dry REST object to be hydrated
	 * @param managed the corresponding managed entity from the DB
	 * @return Return hydrated object
	 */
	<T> T hydrate(T dry, T managed);

	/**
	 * Delete given entity.
	 *
	 * @param <T> the identity class
	 * @param entity the entity to be removed
	 * @return Return detached entity without the ID
	 */
	<T extends Identity<?>> T delete(T entity);

	/**
	 * Delete list of entities.
	 *
	 * @param <T> the identity class
	 * @param entities the entities to be removed
	 * @return Return detached entities without the ID
	 */
	<T extends Identity<?>> Collection<T> delete(Collection<T> entities);

	/**
	 * Delete entity of given class with given ID.
	 *
	 * @param <T> the identity class
	 * @param clazz the entity class
	 * @param id the entity ID
	 * @return Return true if entity was removed, false otherwise
	 */
	<T extends Identity<?>> boolean delete(Class<T> clazz, Serializable id);

	/**
	 * Evict entity.
	 *
	 * @param <T> the generic type of entity to be evicted
	 * @param entity the entity to be evicted
	 * @return Collection of evicted entities
	 */
	<T extends Identity<?>> T evict(T entity);

	/**
	 * Evict list of entities.
	 *
	 * @param <T> the generic type of entities to be evicted
	 * @param entities the collection of entities to be evicted
	 * @return Evicted entities
	 */
	<T extends Identity<?>> Collection<T> evict(Collection<T> entities);

	/**
	 * Initialize all lazy-loaded first-level entities which are not JSON-ignored.
	 *
	 * @param <T> the identity class
	 * @param entity the entity from which fields will be lazy loaded
	 * @return Return the same object with lazy fields initialized
	 */
	<T> T lazyload(T entity);

	<T extends Identity<?>> T load(Class<T> clazz, Serializable id);

}