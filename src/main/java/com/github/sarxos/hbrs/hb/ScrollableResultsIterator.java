package com.github.sarxos.hbrs.hb;

import java.util.Iterator;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;


public class ScrollableResultsIterator<T> implements Iterator<T> {

	private static final int DEFAULT_FLUSH_LIMIT = PersistenceKeeper.getBatchSize();

	private ScrollableResults sr;
	private T next = null;
	private Session session;
	private long count = 0;

	public ScrollableResultsIterator(ScrollableResults sr, Session session) {
		this.sr = sr;
		this.session = session;
	}

	/**
	 * ScrollableResults does not provide a hasNext method, implemented here for
	 * Iterator interface.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean hasNext() {

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
			sr.close();
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
}
