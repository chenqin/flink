package org.apache.flink.formats.thrift;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;

/**
 * ThriftOption defines class name and skip-corrupt-message.
 */
public class ThriftOptions {

	public static final ConfigOption<String> ThriftClassName = ConfigOptions
		.key("thrift-class")
		.stringType()
		.noDefaultValue()
		.withDescription("thrift TBase class name to infer schema");

	public static final ConfigOption<Boolean> skipCorruptedMessage = ConfigOptions
		.key("skip-corrupt-message")
		.booleanType()
		.defaultValue(true)
		.withDescription("return null and skip corrupted messages");
}
