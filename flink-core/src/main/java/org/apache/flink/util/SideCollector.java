package org.apache.flink.util;

/**
 * Created by cq on 8/15/16.
 */
public interface SideCollector<T> {
	abstract void sideCollect(T object);
}
