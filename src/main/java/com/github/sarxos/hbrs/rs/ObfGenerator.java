package com.github.sarxos.hbrs.rs;

import org.eclipse.jetty.util.security.Password;


public class ObfGenerator {

	public static void main(String[] args) {
		Password.main(new String[] { "secret" });
	}
}
