package com.github.sarxos.hbrs.hb;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Worker<K extends PersistenceKeeper, T> implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Worker.class);

	private final class Runner extends Thread {

		public Runner(Runnable r, String name) {
			super(r, name);
			setDaemon(true);
		}
	}

	private final Class<K> clazz;

	private final LinkedBlockingQueue<T> items;

	private final String name;
	private final Runner runner;
	private final boolean stateless;
	private final int capacity;

	private AtomicBoolean running = new AtomicBoolean(false);

	/**
	 * @param clazz the persistence keeper class to work with
	 * @param name the worker name (will be used as thread name)
	 */
	public Worker(Class<K> clazz, String name) {
		this(clazz, name, false);
	}

	/**
	 * @param clazz the persistence keeper class to work with
	 * @param name the worker name (will be used as thread name)
	 * @param start should worker start immediately
	 */
	public Worker(Class<K> clazz, String name, boolean start) {
		this(clazz, name, start, false);
	}

	/**
	 * @param clazz the persistence keeper class to work with
	 * @param name the worker name (will be used as thread name)
	 * @param start should worker start immediately
	 * @param stateless is worker stateless (will not create Hibernate session)
	 */
	public Worker(Class<K> clazz, String name, boolean start, boolean stateless) {
		this(clazz, name, 0, start, stateless);
	}

	/**
	 * @param clazz the persistence keeper class
	 * @param name the worker name
	 * @param capacity the worker capacity
	 * @param start shall worker start immediately
	 * @param stateless is worker stateless (will not create Hibernate session)
	 */
	public Worker(Class<K> clazz, String name, int capacity, boolean start, boolean stateless) {

		this.clazz = clazz;
		this.name = name;
		this.runner = new Runner(this, name);
		this.stateless = stateless;
		this.capacity = capacity;
		this.items = new LinkedBlockingQueue<T>(capacity > 0 ? capacity : Integer.MAX_VALUE);

		if (start) {
			start();
		}
	}

	public void start() {
		if (running.compareAndSet(false, true)) {
			runner.start();
		}
	}

	public void stop() {
		if (running.compareAndSet(true, false)) {
			LOG.info("Message persister has been stopped");
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	public void process(Collection<T> entities) {
		for (T entity : entities) {
			process(entity);
		}
	}

	public void process(T item) {

		if (!isRunning()) {
			throw new RuntimeException("Worker is not running");
		}

		int size = items.size();

		if (capacity > 0 && size > capacity * 0.95) {

			int drain = (int) (capacity * 0.25);

			LOG.error("Worker {} capacity problem detected!", name);
			LOG.error("Worker queue is almost completely excited ({}/{}), draining {} items", size, capacity, drain);

			for (int i = 0; i < drain; i++) {
				if (items.isEmpty()) {
					break;
				}
				try {
					items.take();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		try {
			this.items.put(item);
		} catch (InterruptedException e) {
			LOG.debug("Interrupted: " + e.getMessage(), e);
		}
	}

	public abstract void work(K keeper, Session session, T entity);

	private void commit(Transaction t) {
		if (t != null && t.isActive()) {
			try {
				t.commit();
			} catch (HibernateException e) {
				t.rollback();
				LOG.error(e.getMessage(), e);
			}
		}
	}

	private void flush(K k, Session s) {
		if (s != null && s.isOpen()) {
			try {
				s.flush();
			} catch (HibernateException e) {
				LOG.error(e.getMessage(), e);
			} finally {
				s.clear();
				k.close();
			}
		}
	}

	private void flush(Session s) {
		try {
			s.flush();
		} catch (HibernateException e) {
			LOG.error(e.getMessage(), e);
		} finally {
			s.clear();
		}
	}

	private void rollback(Transaction t, Session s, K k) {
		try {
			t.rollback();
		} catch (Exception e2) {
			LOG.error("Cannot rollback", e2);
		} finally {
			s.clear();
			k.close();
		}
	}

	@Override
	public void run() {

		T m = null;

		int bs = PersistenceKeeperImpl.getBatchSize();
		int c = 0;

		K k = null;
		Transaction t = null;
		Session s = null;

		while (isRunning()) {
			try {
				if (items.isEmpty()) {
					if (!stateless) {
						commit(t);
						flush(k, s);
						c = 0;
					}
					LOG.debug("All awaiting items has been worked out");
				}

				m = items.take();

				if (!stateless) {
					if (s == null || !s.isOpen()) {
						k = create();
						s = k.session();
						t = s.beginTransaction();
					}
				}

				try {
					work(k, s, m);
				} catch (HibernateException e) {
					LOG.error("Hibernate error", e);
					if (!stateless) {
						rollback(t, s, k);
					}
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}

				if (!stateless) {
					if (c > 0 && c++ % bs == 0) {
						commit(t);
						flush(s);
						t = s.beginTransaction();
					}
				}

			} catch (InterruptedException e) {
				LOG.warn("Worker has been interrupted", e);
				return;
			} catch (Exception e) {

				LOG.error("Exception when working: " + e.getMessage(), e);

				try {
					k.close();
				} catch (Exception e1) {
					LOG.error("Exception when closing keeper: " + e1.getMessage(), e1);
				}
			}
		}
	}

	protected K create() {

		K keeper = null;
		try {
			keeper = clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		return keeper;
	}
}
