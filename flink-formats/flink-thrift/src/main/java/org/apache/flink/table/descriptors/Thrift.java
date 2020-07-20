package org.apache.flink.table.descriptors;

import java.util.Map;

/**
 * Thrift format descriptor.
 */
public class Thrift extends FormatDescriptor{
	boolean ignoreParseStructErrors = false;

	public Thrift() {
		super(ThriftValidator.FORMAT_TYPE_VALUE, 1);
	}

	public Thrift ignoreParseStructErrors(boolean ignoreParseStructErrors) {
		this.ignoreParseStructErrors = ignoreParseStructErrors;
		return this;
	}

	@Override
	protected Map<String, String> toFormatProperties() {
		final DescriptorProperties properties = new DescriptorProperties();
		properties.putBoolean(ThriftValidator.IGNORE_PARSE_STRUCT_ERRORS, ignoreParseStructErrors);
		return properties.asMap();
	}
}
