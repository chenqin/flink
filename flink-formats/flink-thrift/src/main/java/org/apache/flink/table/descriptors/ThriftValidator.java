package org.apache.flink.table.descriptors;

import org.apache.flink.annotation.Internal;

import org.apache.flink.table.api.ValidationException;

import org.apache.thrift.TBase;

/**
 * Validator for {@link Thrift}.
 */
@Internal
public class ThriftValidator extends FormatDescriptorValidator {
	public static final String FORMAT_TYPE_VALUE = "thrift";
	public static final String FORMAT_SCHEMA = "format.thrift.class";

	@Override
	public void validate(DescriptorProperties properties) {
		super.validate(properties);
		try {
			Class<?> t = Class.forName(FORMAT_SCHEMA);
			t.asSubclass(TBase.class);
		} catch (ClassNotFoundException e) {
			throw new ValidationException("can't load thrift class");
		} catch (ClassCastException e) {
			throw new ValidationException("thrift.class should extends from TBase");
		}
	}
}
