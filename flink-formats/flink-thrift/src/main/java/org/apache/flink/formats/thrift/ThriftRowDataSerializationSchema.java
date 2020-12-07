package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.RowType;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TSerializer;
import org.apache.thrift.meta_data.EnumMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TType;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThriftRowDataSerializationSchema implements SerializationSchema<RowData> {
	/** return null if not able to deserialize message **/
	private Boolean skipCorruptedMessage;
	/** TypeInformation of the produced {@link RowData}. **/
	private final RowType rowType;

	Class<? extends TBase> thriftClass;

	/** The converter that converts internal data formats to JsonNode. */
	private final SerializationRuntimeConverter runtimeConverter;

	private final TSerializer serializer;

	public ThriftRowDataSerializationSchema(
		Boolean skipCorruptedMessage,
		Class<? extends TBase> thriftClass,
		RowType rowType) {
		this.skipCorruptedMessage = skipCorruptedMessage;
		this.thriftClass = thriftClass;
		this.rowType = rowType;
		this.runtimeConverter = createConverter(rowType, null);
		serializer = new TSerializer();
	}

	/**
	 * Serializes the incoming element to a specified type.
	 *
	 * @param element The incoming element to be serialized
	 * @return The serialized element.
	 */
	@Override
	public byte[] serialize(RowData element) {
		try {
			return serializer.serialize((TBase) runtimeConverter.convert(element));
		} catch (Exception e) {
			if (skipCorruptedMessage) {
				return new byte[0];
			} else {
				throw new RuntimeException("can't encode row data", e);
			}
		}
	}

	/**
	 * Runtime converter that converts objects of Flink Table & SQL internal data structures
	 * to corresponding {@link TBase}s.
	 */
	private interface SerializationRuntimeConverter extends Serializable {
		Object convert(Object value) throws NoSuchMethodException;
	}

	/**
	 * Creates a runtime converter which is null safe.
	 */
	private SerializationRuntimeConverter createConverter(LogicalType type, FieldValueMetaData meta) {
		return wrapIntoNullableConverter(createNotNullConverter(type, meta));
	}

	/**
	 * Creates a runtime converter which assuming input object is not null.
	 * TODO: enum was represent as INT, need to encode properly
	 * TODO: ByteBuffer were represented as string need to check thrift type and do ByteBuffer wrap
	 */
	private SerializationRuntimeConverter createNotNullConverter(
		LogicalType type, FieldValueMetaData metaData) {
		switch (type.getTypeRoot()) {
			case NULL:
				return value -> null;
			case BOOLEAN:
				return  value -> (boolean) value;
			case TINYINT:
				return value -> {
					if(metaData.type == TType.BYTE) {
						return (Byte) value;
					} else {
						return value;
					}
				};
			case SMALLINT:
				return value ->(short) value;
			case INTEGER:
			    return value -> {
			    	if (metaData instanceof EnumMetaData) {
			    		try {
						    EnumMetaData enumMetaData = (EnumMetaData) metaData;
						    return enumMetaData.enumClass.getMethod("findByValue", int.class)
								.invoke(null, (int) value);
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						} catch (InvocationTargetException e) {
							e.printStackTrace();
						}
					}
					return (int) value;
				};
			case BIGINT:
				return value -> (long) value;
			case FLOAT:
				return value ->(float) value;
			case DOUBLE:
				return value -> (double) value;
			case CHAR:
			case VARCHAR:
				// value is BinaryString
				return value -> value.toString();
			case BINARY:
			case VARBINARY:
				return value -> {
					if (metaData.type == TType.STRING) {
						return ByteBuffer.wrap((byte[]) value);
					}
					if (metaData.type == TType.BYTE) {
						return ((byte[])value)[0];
					}
					return (byte[])value;
				};
			case ARRAY:
				return value -> {
					ArrayType arrayType = (ArrayType) type;
					ArrayData arrayData = (ArrayData) value;
					final LogicalType elementType = arrayType.getElementType();
					if(metaData instanceof ListMetaData) {
						List list = new ArrayList<Object>(((ArrayData) value).size());
						ListMetaData listMetaData = (ListMetaData) metaData;
						final SerializationRuntimeConverter valueConverter = createConverter(
							elementType,
							listMetaData.elemMetaData);
						for (int i = 0; i < ((ArrayData) value).size(); i++) {
							Object element = ArrayData.get(arrayData, i, elementType);
							list.add(valueConverter.convert(element));
						}
						return list;
					} else if(metaData instanceof SetMetaData){
						Set set = new HashSet(((ArrayData) value).size());
						SetMetaData setMetaData = (SetMetaData) metaData;
						final SerializationRuntimeConverter valueConverter = createConverter(
							elementType,
							setMetaData.elemMetaData);
						for (int i = 0; i < ((ArrayData) value).size(); i++) {
							Object element = ArrayData.get(arrayData, i, elementType);
							set.add(valueConverter.convert(element));
						}
						return set;
					} else {
						throw new RuntimeException(metaData.toString());
					}
				};
			case MAP:
				return value -> {
					MapData map = (MapData) value;
					MapType mapType = (MapType) type;
					MapMetaData mapMetaData = (MapMetaData) metaData;

					final LogicalType keyType = mapType.getKeyType();
					final LogicalType valueType = mapType.getValueType();

					final SerializationRuntimeConverter keyConverter = createConverter(keyType, mapMetaData.keyMetaData);
					final SerializationRuntimeConverter valueConverter = createConverter(valueType, mapMetaData.valueMetaData);
					ArrayData keyArray = map.keyArray();
					ArrayData valueArray = map.valueArray();
					int numElements = map.size();
					Map result = new HashMap<>();
					for (int i = 0; i < numElements; i++) {
						Object key = ArrayData.get(keyArray,i, keyType);
						Object val = ArrayData.get(valueArray, i, valueType);
						result.put(keyConverter.convert(key), valueConverter.convert(val));
					}
					return result;
				};
			case ROW:
				// convert a row to tbase class instance with thrift class
				return value -> {
					RowType rowType = (RowType) type;

					final LogicalType[] fieldTypes = rowType.getFields().stream()
						.map(RowType.RowField::getType)
						.toArray(LogicalType[]::new);

					// handle nested class
					if(metaData != null && metaData instanceof StructMetaData) {
						thriftClass = ((StructMetaData) metaData).structClass;
					}

					final TFieldIdEnum[] fields =
						ThriftRowTranslator.getSortedFields(thriftClass, null).stream().map(entry ->{
							return entry.getKey();
						}).toArray(TFieldIdEnum[]::new);

					final FieldValueMetaData[] values =
						ThriftRowTranslator.getSortedFields(thriftClass, null).stream().map(entry ->{
							return entry.getValue().valueMetaData;
						}).toArray(FieldValueMetaData[]::new);

					final int fieldCount = rowType.getFieldCount();
					RowData row = (RowData) value;
					TBase tbase = ThriftRowTranslator.getReusableInstance(thriftClass);

					for (int i = 0; i < fieldCount; i++) {
						Object field = RowData.get(row, i, fieldTypes[i]);
						SerializationRuntimeConverter converter = createConverter(fieldTypes[i], values[i]);
						tbase.setFieldValue(fields[i], converter.convert(field));
					}
					return tbase;
				};
			case RAW:
			default:
				throw new UnsupportedOperationException("Not support to parse type: " + type);
		}
	}

	private SerializationRuntimeConverter wrapIntoNullableConverter(
		SerializationRuntimeConverter converter) {
		return object -> {
			if (object == null) {
				return null;
			}

			return converter.convert(object);
		};
	}
}
