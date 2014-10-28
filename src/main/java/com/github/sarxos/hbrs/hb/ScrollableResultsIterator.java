package com.github.sarxos.hbrs.hb;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ScrollableResultsIterator<T> implements Iterator<T>, Closeable {

	/**
	 * I'm the logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(ScrollableResultsIterator.class);

	private static final int DEFAULT_FLUSH_LIMIT = PersistenceKeeperImpl.getBatchSize();

	private ScrollableResults sr;
	private T next = null;
	private Session session;
	private long count = 0;
	private AtomicBoolean open = new AtomicBoolean(true);

	public ScrollableResultsIterator(ScrollableResults sr, Session session) {
		this.sr = sr;
		this.session = session;
	}

	/**
	 * ScrollableResults does not provide a hasNext method, implemented here for Iterator interface.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean hasNext() {

		if (!open.get()) {
			throw new IllegalStateException("Cursor iterator is already closed");
		}

		if (sr.next()) {
			next = (T) sr.get()[0];
		} else {
			sr.close();
		}

		return next != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T next() {

		if (!open.get()) {
			throw new IllegalStateException("Cursor iterator is already closed");
		}

		T toReturn = null;

		// next variable could be null because the last element was sent or
		// because we iterate on the next()
		// instead of hasNext()

		if (next == null) {
			// if we can retrieve an element, do it
			if (sr.next()) {
				toReturn = (T) sr.get()[0];
			}
		} else {
			// the element was fetched by hasNext, return it
			toReturn = next;
			next = null;
		}

		// if we are at the end, close the result set

		if (toReturn == null) {
			try {
				close();
			} catch (IOException e) {
				LOG.error("IO exception when closing cursor", e);
			}
		} else {

			// clear memory to avoid memory leak
			if (count > 0 && count % DEFAULT_FLUSH_LIMIT == 0) {
				flush();
			}

			count++;
		}

		return toReturn;
	}

	public void flush() {
		session.flush();
		session.clear();
	}

	/**
	 * Unsupported Operation for this implementation.
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() throws IOException {
		if (open.compareAndSet(true, false)) {
			sr.close();
			flush();
		}
	}
}
