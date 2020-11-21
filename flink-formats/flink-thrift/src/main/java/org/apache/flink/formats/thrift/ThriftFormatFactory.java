package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.format.DecodingFormat;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.factories.DeserializationFormatFactory;
import org.apache.flink.table.factories.DynamicTableFactory;
import org.apache.flink.table.factories.FactoryUtil;
import org.apache.flink.table.factories.SerializationFormatFactory;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;

import org.apache.flink.util.Preconditions;

import org.apache.thrift.TBase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Table format factory for providing configured instances of Thrift to RowData
 * {@link SerializationSchema} and {@link DeserializationSchema}.
 */
public class ThriftFormatFactory implements DeserializationFormatFactory, SerializationFormatFactory {

	public static final String IDENTIFIER = "thrift";

	/**
	 * Creates a format from the given context and format options.
	 *
	 * <p>The format options have been projected to top-level options (e.g. from {@code key.format.ignore-errors}
	 * to {@code format.ignore-errors}).
	 *
	 * @param context
	 * @param formatOptions
	 */
	@Override
	public DecodingFormat<DeserializationSchema<RowData>> createDecodingFormat(
		DynamicTableFactory.Context context,
		ReadableConfig formatOptions) {

		String thriftClassName = formatOptions.get(ThriftOptions.ThriftClassName);
		Boolean skipCorruptedMessage = formatOptions.get(ThriftOptions.skipCorruptedMessage);
		Class<? extends TBase> thriftClass = null;
		TBase instance = null;
		try {
			thriftClass = (Class<? extends TBase>) Class.forName(thriftClassName);
			instance = thriftClass.newInstance();
		} catch (ClassNotFoundException e) {
			throw new ValidationException("can't load thrift class");
		} catch (ClassCastException e) {
			throw new ValidationException("can't cast thrift class to Tbase extend");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		}
		final TableSchema schema = ThriftRowTranslator.getTableSchema(thriftClass);

		Preconditions.checkNotNull(instance);
		final TBase finalInstance = instance;
		return new DecodingFormat<DeserializationSchema<RowData>>() {
			@Override
			public DeserializationSchema<RowData> createRuntimeDecoder(
				DynamicTableSource.Context context,
				DataType producedDataType) {
				// infer table schema
				final TypeInformation<RowData> rowDataTypeInfo =
					(TypeInformation<RowData>) context.createTypeInformation(
						schema.toRowDataType());
				return new ThriftRowDataDeserializationSchema(skipCorruptedMessage,
					finalInstance, rowDataTypeInfo);
			}

			@Override
			public ChangelogMode getChangelogMode() { return ChangelogMode.insertOnly(); }
		};
	}

	/**
	 * Creates a format from the given context and format options.
	 *
	 * <p>The format options have been projected to top-level options (e.g. from {@code key.format.ignore-errors}
	 * to {@code format.ignore-errors}).
	 *
	 * @param context
	 * @param formatOptions
	 */
	@Override
	public EncodingFormat<SerializationSchema<RowData>> createEncodingFormat(
		DynamicTableFactory.Context context,
		ReadableConfig formatOptions) {
		//TODO: add here
		return null;
	}

	/**
	 * Returns a unique identifier among same factory interfaces.
	 *
	 * <p>For consistency, an identifier should be declared as one lower case word (e.g. {@code kafka}). If
	 * multiple factories exist for different versions, a version should be appended using "-" (e.g. {@code kafka-0.10}).
	 */
	@Override
	public String factoryIdentifier() {
		return IDENTIFIER;
	}

	/**
	 * Returns a set of {@link ConfigOption} that an implementation of this factory requires in addition to
	 * {@link #optionalOptions()}.
	 *
	 * <p>See the documentation of {@link Factory} for more information.
	 */
	@Override
	public Set<ConfigOption<?>> requiredOptions() {
		Set<ConfigOption<?>> options = new HashSet<>();
		options.add(ThriftOptions.ThriftClassName);
		return options;
	}

	/**
	 * Returns a set of {@link ConfigOption} that an implementation of this factory consumes in addition to
	 * {@link #requiredOptions()}.
	 *
	 * <p>See the documentation of {@link Factory} for more information.
	 */
	@Override
	public Set<ConfigOption<?>> optionalOptions() {
		return Collections.emptySet();
	}
}
