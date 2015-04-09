package com.github.sarxos.hbrs.hb;

import java.nio.file.Paths;

import javax.annotation.ManagedBean;

import com.github.sarxos.hbrs.hb.util.ManagedBeansUtils;


@ManagedBean("runtime-path-resolver")
public abstract class PersistenceFactoryPathResolverImpl implements PersistenceFactoryPathResolver {

	private String root;

	public PersistenceFactoryPathResolverImpl(String root) {
		this.root = root;
		register();
	}

	@Override
	public String resolve(String path) {
		if (Paths.get(path).isAbsolute()) {
			return path;
		} else {
			return Paths.get(root, path).toString();
		}
	}

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}

	public void register() {
		ManagedBeansUtils.register(this);
	}

	public void unregister() {
		ManagedBeansUtils.unregister(this);
	}

	public static String resolvePath(String path) {
		Object[] args = new Object[] { path };
		String[] signature = new String[] { String.class.getName() };
		return (String) ManagedBeansUtils.invoke(PersistenceFactoryPathResolverImpl.class, "resolve", args, signature);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[to:" + root + "]";
	}
}
