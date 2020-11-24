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
import org.apache.thrift.TEnum;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TSerializer;
import org.apache.thrift.meta_data.EnumMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

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
		this.runtimeConverter = createConverter(rowType, thriftClass, null);
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
	private SerializationRuntimeConverter createConverter(LogicalType type, Class<? extends TBase> thriftClass, Class<? extends TEnum> enumClass) {
		return wrapIntoNullableConverter(createNotNullConverter(type, thriftClass, enumClass));
	}

	/**
	 * Creates a runtime converter which assuming input object is not null.
	 * TODO: enum was represent as INT, need to encode properly
	 * TODO: ByteBuffer were represented as string need to check thrift type and do ByteBuffer wrap
	 */
	private SerializationRuntimeConverter createNotNullConverter(
		LogicalType type, Class<? extends TBase> thriftClass, Class<? extends TEnum> enumClass) {
		switch (type.getTypeRoot()) {
			case NULL:
				return value -> null;
			case BOOLEAN:
				return  value -> (boolean) value;
			case TINYINT:
				return value -> (byte) value;
			case SMALLINT:
				return value ->(short) value;
			case INTEGER:
			    return value -> {
			    	if (enumClass != null && thriftClass == null) {
			    		try {
							return enumClass.getMethod("findByValue", int.class)
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
				return value -> (byte[]) value;
			case ARRAY:
				return value -> {
					ArrayType arrayType = (ArrayType) type;
					ArrayData arrayData = (ArrayData) value;
					Object[] arr = new Object[((ArrayData) value).size()];
					final LogicalType elementType = arrayType.getElementType();
					final SerializationRuntimeConverter elementConverter = createConverter(elementType, thriftClass, null);
					for( int i = 0 ; i < ((ArrayData) value).size(); i++) {
						Object element = ArrayData.get(arrayData, i, elementType);
						arr[i] = elementConverter.convert(element);
					}
					return arr;
				};
			case MAP:
			case MULTISET:
				return value -> {
					MapData map = (MapData) value;
					MapType mapType = (MapType) type;
					final LogicalType valueType = mapType.getValueType();
					//TODO: we only support value type
					final SerializationRuntimeConverter valueConverter = createConverter(valueType, thriftClass, null);
					ArrayData keyArray = map.keyArray();
					ArrayData valueArray = map.valueArray();
					int numElements = map.size();
					Map result = new HashMap<Object, Object>();
					for (int i = 0; i < numElements; i++) {
						String fieldName = keyArray.getString(i).toString(); // key must be string
						Object val = ArrayData.get(valueArray, i, valueType);
						result.put(fieldName, valueConverter.convert(val));
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

					final TFieldIdEnum[] fields =
						ThriftRowTranslator.getSortedFields(thriftClass, null).stream().map(entry ->{
							return entry.getKey();
						}).toArray(TFieldIdEnum[]::new);

					final Class[] thriftClasses =
						ThriftRowTranslator.getSortedFields(thriftClass, null).stream().map(entry ->{
							Class t =  ThriftRowTranslator.getThriftClass(entry.getValue().valueMetaData);
							return (t != null && !t.isEnum()) ? t : null;
						}).toArray(Class[]::new);

					final Class[] enumClasses =
						ThriftRowTranslator.getSortedFields(thriftClass, null).stream().map(entry ->{
							Class t =  ThriftRowTranslator.getThriftClass(entry.getValue().valueMetaData);
							return (t != null && t.isEnum()) ? t : null;
						}).toArray(Class[]::new);

					final int fieldCount = rowType.getFieldCount();
					RowData row = (RowData) value;
					TBase tbase = ThriftRowTranslator.getReusableInstance(thriftClass);

					for (int i = 0; i < fieldCount; i++) {
						Object field = RowData.get(row, i, fieldTypes[i]);
						SerializationRuntimeConverter converter = createConverter(fieldTypes[i], thriftClasses[i], enumClasses[i]);
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
