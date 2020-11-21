package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.table.descriptors.DescriptorProperties;
import org.apache.flink.table.factories.DeserializationSchemaFactory;
import org.apache.flink.table.factories.SerializationSchemaFactory;
import org.apache.flink.table.factories.TableFormatFactoryBase;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Table format factory for providing configured instances of Thrift-to-row {@link SerializationSchema}
 * and {@link DeserializationSchema}.
 */
public class ThriftRowFormatFactory extends TableFormatFactoryBase<Row>
	implements SerializationSchemaFactory<Row>, DeserializationSchemaFactory<Row> {

	public ThriftRowFormatFactory(String type, int version, boolean supportsSchemaDerivation) {
		super(type, version, supportsSchemaDerivation);
	}

	/**
	 * Creates and configures a {@link DeserializationSchema} using the given properties.
	 *
	 * @param properties normalized properties describing the format
	 * @return the configured serialization schema or null if the factory cannot provide an
	 * instance of this class
	 */
	@Override
	public DeserializationSchema<Row> createDeserializationSchema(Map<String, String> properties) {
		return null;
	}

	/**
	 * Creates and configures a [[SerializationSchema]] using the given properties.
	 *
	 * @param properties normalized properties describing the format
	 * @return the configured serialization schema or null if the factory cannot provide an
	 * instance of this class
	 */
	@Override
	public SerializationSchema<Row> createSerializationSchema(Map<String, String> properties) {
		return null;
	}
}
