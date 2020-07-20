package org.apache.flink.table.descriptors;

import org.apache.flink.table.api.ValidationException;

import org.apache.thrift.TBase;

/**
 * Thrift validator.
 */
public class ThriftValidator extends FormatDescriptorValidator{
	public static final String FORMAT_TYPE_VALUE = "thrift";
	public static final String IGNORE_PARSE_STRUCT_ERRORS = "format.ignore-parse-struct-errors";

	@Override
	public void validate(DescriptorProperties properties) {
		try {
			Class<? extends TBase> tinstance = (Class<? extends TBase>) ClassLoader.getSystemClassLoader()
				.loadClass(properties.getString(FORMAT_TYPE));
		} catch (ClassNotFoundException e) {
			throw new ValidationException(e.getMessage());
		}
		properties.validateBoolean(IGNORE_PARSE_STRUCT_ERRORS, false);
		super.validate(properties);
	}
}
