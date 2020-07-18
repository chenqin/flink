package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.types.Row;

import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Deserialize a thrift formed byte array into a table row format.
 */
public class ThriftRowDeserializationSchema implements DeserializationSchema<Row> {

	/**
	 * Flag indicating whether to ignore invalid fields/rows (default: throw an exception).
	 */
	private final boolean ignoreParseStructErrors;
	private final Class<? extends TBase> thriftClass;
	private final TDeserializer td = new TDeserializer();
	private boolean failOnParserField;
	private transient TBase instance;

	public ThriftRowDeserializationSchema(Class<? extends TBase> thriftClass, boolean ignoreParseStructErrors) {
		checkNotNull(thriftClass, "Thrift class");
		if (ignoreParseStructErrors && this.failOnParserField) {
			throw new IllegalArgumentException(
				"Thrift format doesn't support failOnParserField and ignoreParseStructErrors are both true.");
		}
		this.failOnParserField = this.failOnParserField;
		this.ignoreParseStructErrors = ignoreParseStructErrors;
		this.thriftClass = thriftClass;
		try {
			this.instance = thriftClass.getDeclaredConstructor().newInstance();
		} catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Row deserialize(byte[] message) throws IOException {
		try {
			td.deserialize(instance, message);
			return ThriftConverter.getRow(instance, failOnParserField);
		} catch (TException e) {
			if (ignoreParseStructErrors) {
				return null;
			}
			throw new IOException(e);
		}
	}

	@Override
	public boolean isEndOfStream(Row nextElement) {
		return false;
	}

	@Override
	public TypeInformation<Row> getProducedType() {
		return ThriftConverter.getRowTypeInfo(thriftClass);
	}
}
