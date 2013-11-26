package com.github.sarxos.hbrs.hb;

import java.io.Serializable;


public interface Identity<T extends Serializable> {

	void setId(T id);

	T getId();

}
