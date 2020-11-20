package org.apache.flink.table.descriptors;

import java.util.Map;

import static org.apache.flink.table.descriptors.FormatDescriptorValidator.FORMAT_DERIVE_SCHEMA;

public class Thrift extends FormatDescriptor{

	public Thrift() {
		super(ThriftValidator.FORMAT_TYPE_VALUE, 1);
	}

	/**
	 * Converts this descriptor into a set of format properties. Usually prefixed with
	 * {@link FormatDescriptorValidator#FORMAT}.
	 */
	@Override
	protected Map<String, String> toFormatProperties() {
		final DescriptorProperties properties = new DescriptorProperties();
		properties.putBoolean(FORMAT_DERIVE_SCHEMA, true);
		return properties.asMap();
	}
}
