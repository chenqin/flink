package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.EnumMetaData;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * inference thrift schema to TableSchema.
 * decode TBase thrift object to Row.
 */
public class ThriftRowTranslator {

	private static final Logger LOG = LoggerFactory.getLogger(ThriftRowTranslator.class);

	public static Class<?> getThriftClass(FieldValueMetaData metaData) {
		switch (metaData.type) {
			case TType.ENUM:
				EnumMetaData enumMetaData = (EnumMetaData) metaData;
				return enumMetaData.enumClass;
			case TType.LIST:
				ListMetaData listMetaData = (ListMetaData) metaData;
				return getThriftClass(listMetaData.elemMetaData);
			case TType.SET:
				SetMetaData setMetaData = (SetMetaData) metaData;
				return getThriftClass(setMetaData.elemMetaData);
			case TType.MAP:
				// TODO: only support value class for now
				MapMetaData mapMetaData = (MapMetaData) metaData;
				return getThriftClass(mapMetaData.valueMetaData);
			case TType.STRUCT:
				StructMetaData structMetaData = (StructMetaData) metaData;
				return structMetaData.structClass;
			default:
				return null;
		}
	}

	public static EnumMetaData getEnum(FieldValueMetaData metaData) {
		switch (metaData.type) {
			case TType.ENUM:
				return (EnumMetaData) metaData;
			default:
				return null;
		}
	}

	public static List<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> getSortedFields(
		Class<? extends TBase> thriftClass, TBase tBase) {
		Map<? extends TFieldIdEnum, FieldMetaData> metaDataMap;

		try {
			metaDataMap =
				(Map<? extends TFieldIdEnum, FieldMetaData>) thriftClass.getField("metaDataMap")
					.get(tBase);
			ArrayList<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
				new ArrayList<>(metaDataMap.entrySet());

			// align field index with discreet thrift id
			entries.sort(new Comparator<Map.Entry<? extends TFieldIdEnum, FieldMetaData>>() {
				@Override
				public int compare(
					Map.Entry<? extends TFieldIdEnum, FieldMetaData> o1,
					Map.Entry<? extends TFieldIdEnum, FieldMetaData> o2) {
					return o1.getKey().getThriftFieldId() - o2.getKey().getThriftFieldId();
				}
			});
			return entries;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**translate thrift TType into blink Table Types.
	 * @param metaData thrift class value metadata.
	 *
	 * @return Flink TypeInformation.
	 */
	private static TypeInformation getType(FieldValueMetaData metaData) {
		switch (metaData.type) {
			case TType.BOOL:
				return Types.BOOLEAN;
			case TType.BYTE:
				return Types.BYTE;
			case TType.DOUBLE:
				return Types.DOUBLE;
			case TType.I64:
				return Types.BIG_INT;
			case TType.I16:
				return Types.SHORT;
			case TType.I32:
				return Types.INT;
			case TType.ENUM:
				return Types.INT;
			case TType.STRING:
				return Types.STRING;
			case TType.LIST:
				ListMetaData listMetaData = (ListMetaData) metaData;
				return Types.LIST(getType(listMetaData.elemMetaData));
			case TType.SET:
				SetMetaData setMetaData = (SetMetaData) metaData;
				return Types.LIST(getType(setMetaData.elemMetaData));
			case TType.MAP:
				MapMetaData mapMetaData = (MapMetaData) metaData;
				return Types.MAP(
					getType(mapMetaData.keyMetaData),
					getType(mapMetaData.valueMetaData));
			case TType.STRUCT:
				StructMetaData structMetaData = (StructMetaData) metaData;
				RowTypeInfo nest = getRowTypeInfo(structMetaData.structClass);
				return Types.ROW_NAMED(nest.getFieldNames(), nest.getFieldTypes());
			default:
				throw new RuntimeException("can't handle type");
		}
	}

	/**
	 * return possible nested type information of each columns in a row, sorted by thrift id
	 *
	 * @param thriftClass thrift class name.
	 *
	 * @return RowTypeInfo used to annotate DatStream <Row> schema
	 * 	<p>
	 * 	eventStream.map(obj -> getRow(obj)).returns(getRowTypeInfo(TBase.class));
	 */
	@Deprecated
	public static RowTypeInfo getRowTypeInfo(Class<? extends TBase> thriftClass) {
		List<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
			getSortedFields(thriftClass, null);

		TypeInformation[] types = new TypeInformation[entries.size()];
		String[] fields = new String[entries.size()];
		int i = 0;

		for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
			fields[i] = entry.getValue().fieldName;
			types[i] = getType(entry.getValue().valueMetaData);
			i++;
		}

		return new RowTypeInfo(types, fields);
	}

	/**
	 * return DataType of each thrift field.
	 * @return
	 */
	private static DataType getDataType(FieldValueMetaData metaData) throws NoSuchFieldException, IllegalAccessException {
		switch (metaData.type) {
			case TType.BOOL:
				return DataTypes.BOOLEAN();
			case TType.BYTE:
				return DataTypes.TINYINT();
			case TType.DOUBLE:
				return DataTypes.DOUBLE();
			case TType.I64:
				return DataTypes.BIGINT();
			case TType.I16:
				return DataTypes.SMALLINT();
			case TType.I32:
				return DataTypes.INT();
			case TType.ENUM:
				return DataTypes.INT();
			case TType.STRING:
				return metaData.isBinary() ? DataTypes.BYTES() : DataTypes.STRING();
			case TType.LIST:
				ListMetaData listMetaData = (ListMetaData) metaData;
				return DataTypes.ARRAY(getDataType(listMetaData.elemMetaData));
			case TType.SET:
				SetMetaData setMetaData = (SetMetaData) metaData;
				return DataTypes.ARRAY(getDataType(setMetaData.elemMetaData));
			case TType.MAP:
				MapMetaData mapMetaData = (MapMetaData) metaData;
				return DataTypes.MAP(
					getDataType(mapMetaData.keyMetaData), getDataType(mapMetaData.valueMetaData));
			case TType.STRUCT:
				StructMetaData structMetaData = (StructMetaData) metaData;
				return DataTypes.ROW(getFields(structMetaData.structClass));
			default:
				throw new RuntimeException("can't handle type");
		}
	}

	/**
	 * parse thrift class and fill field and data types
	 *
	 * @param thriftClass
	 *
	 * @return
	 */
	public static DataTypes.Field[] getFields(Class<? extends TBase> thriftClass)
		throws NoSuchFieldException, IllegalAccessException {
		List<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
			getSortedFields(thriftClass, null);

		DataTypes.Field[] fields = new DataTypes.Field[entries.size()];
		int i = 0;

		for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
			fields[i] = DataTypes.FIELD(
				entry.getValue().fieldName, getDataType(entry.getValue().valueMetaData));
			i++;
		}
		return fields;
	}

	/**
	 * generate table schema from thrift class
	 *
	 * @param thriftClass thrift class.
	 *
	 * @return
	 */
	public static TableSchema getTableSchema(Class<? extends TBase> thriftClass) throws NoSuchFieldException, IllegalAccessException {
		TableSchema.Builder builder = TableSchema.builder();
		DataTypes.Field[] fields = getFields(thriftClass);
		for (int i = 0; i < fields.length; i++) {
			builder.field(fields[i].getName(), fields[i].getDataType());
		}
		return builder.build();
	}

	private static Object getDefaultValue(FieldValueMetaData valueMetaData) {
		switch (valueMetaData.type) {
			case TType.BOOL:
				return false;
			case TType.BYTE:
				return (byte) 0;
			case TType.DOUBLE:
				return (double) 0;
			case TType.STRING:
				return StringData.fromString("");
			case TType.I64:
				return (long) 0;
			case TType.I16:
				return (short) 0;
			case TType.I32:
			case TType.ENUM:
				return 0;
			case TType.LIST:
			case TType.SET:
			case TType.MAP:
			case TType.STRUCT:
				return null;
			default:
				throw new RuntimeException("unhandled default value");
		}
	}

	private static Object getPrimitiveValue(FieldValueMetaData valueMetaData, Object val) throws NoSuchFieldException, IllegalAccessException {
		switch (valueMetaData.type) {
			case TType.BOOL:
			case TType.BYTE:
			case TType.DOUBLE:
			case TType.I64:
			case TType.I16:
			case TType.I32:
				return val;
			case TType.STRING:
				return valueMetaData.isBinary() ? (byte[]) val : StringData.fromString((String) val);
			case TType.ENUM:
				try {
					EnumMetaData enumMetaData = (EnumMetaData) valueMetaData;
					return enumMetaData.enumClass.getMethod("getValue").invoke(val);
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
			case TType.LIST:
				ListMetaData listMetaData = (ListMetaData) valueMetaData;
				List items = (List) val;
				DataType type = getDataType(listMetaData.elemMetaData);
				Object[] transform = (Object[]) java.lang.reflect.Array.newInstance(
					type.getConversionClass(), items.size());
				for (int i = 0; i < items.size(); i++) {
					transform[i] = getPrimitiveValue(listMetaData.elemMetaData, items.get(i));
				}
				return transform;
			case TType.SET:
				SetMetaData setMetaData = (SetMetaData) valueMetaData;
				Set sets = (Set) val;
				DataType elementType = getDataType(setMetaData.elemMetaData);
				Object[] transformSet = (Object[]) java.lang.reflect.Array.newInstance(
					elementType.getConversionClass(), sets.size());
				int j = 0;
				for (Object ele : sets) {
					transformSet[j++] = getPrimitiveValue(setMetaData.elemMetaData, ele);
				}
				return transformSet;
			case TType.MAP:
				MapMetaData mapMetaData = (MapMetaData) valueMetaData;
				Map maps = (Map) val;
				HashMap transferMap = new HashMap();
				maps.keySet().forEach(key -> {
					try {
						transferMap.put(
							getPrimitiveValue(mapMetaData.keyMetaData, key),
							getPrimitiveValue(mapMetaData.valueMetaData, maps.get(key)));
					} catch (NoSuchFieldException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				});
				return transferMap;
			case TType.STRUCT:
				return getRowData((TBase) val);
			default:
				throw new RuntimeException("unhandled primitive type");
		}
	}

	public static RowData getRowData(TBase tBase) throws NoSuchFieldException, IllegalAccessException {
		List<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
			getSortedFields(tBase.getClass(), tBase);

		// allocate row by thrift field size plus reserved field
		GenericRowData result = new GenericRowData(entries.size());
		int i = 0;

		for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
			if (tBase.isSet(entry.getKey())) {
				Object val = tBase.getFieldValue(entry.getKey());
				result.setField(i, getPrimitiveValue(entry.getValue().valueMetaData, val));
			} else {
				result.setField(i, getDefaultValue(entry.getValue().valueMetaData));
			}
			i++;
		}
		return result;
	}

	private static Object rowValueToThriftValue(FieldValueMetaData valueMetaData, Object val) {
		switch (valueMetaData.type) {
			case TType.BOOL:
				return Boolean.valueOf(val.toString());
			case TType.BYTE:
				return Byte.valueOf(val.toString());
			case TType.DOUBLE:
				return Double.valueOf(val.toString());
			case TType.STRING:
				return valueMetaData.isBinary() ? ByteBuffer.wrap((byte[]) val) : (String) val;
			case TType.I64:
				return Long.valueOf(val.toString());
			case TType.I16:
				return Short.valueOf(val.toString());
			case TType.I32:
				return Integer.valueOf(val.toString());
			case TType.ENUM:
				try {
					EnumMetaData enumMetaData = (EnumMetaData) valueMetaData;
					return enumMetaData.enumClass.getMethod("findByValue", int.class)
						.invoke(null, val);
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
			case TType.LIST:
				ListMetaData listMetaData = (ListMetaData) valueMetaData;
				Object[] items = (Object[]) val;
				List<Object> transform = new ArrayList<>();
				for (int i = 0; i < items.length; i++) {
					transform.add(rowValueToThriftValue(listMetaData.elemMetaData, items[i]));
				}
				return transform;
			case TType.SET:
				SetMetaData setMetaData = (SetMetaData) valueMetaData;
				Object[] itemsSet = (Object[]) val;
				Set transformSet = new HashSet();
				for (int i = 0; i < itemsSet.length; i++) {
					transformSet.add(rowValueToThriftValue(setMetaData.elemMetaData, itemsSet[i]));
				}
				return transformSet;
			case TType.MAP:
				MapMetaData mapMetaData = (MapMetaData) valueMetaData;
				Map maps = (Map) val;
				HashMap transferMap = new HashMap();
				maps.keySet().forEach(key -> {
					transferMap.put(
						rowValueToThriftValue(mapMetaData.keyMetaData, key),
						rowValueToThriftValue(mapMetaData.valueMetaData, maps.get(key)));
				});
				return transferMap;
			case TType.STRUCT:
				StructMetaData structMetaData = (StructMetaData) valueMetaData;
				return getThriftObject((Row) val, structMetaData.structClass);
			default:
				throw new RuntimeException("unhandled thrift type");
		}
	}

	public static Class<? extends TBase> getThriftClass(String thriftClassName) {
		Class<? extends TBase> thriftClass = null;
		try {
			thriftClass = (Class<? extends TBase>) Class.forName(thriftClassName);
		} catch (ClassNotFoundException e) {
			throw new ValidationException("can't load thrift class");
		} catch (ClassCastException e) {
			throw new ValidationException("can't cast thrift class to TBase extend");
		}
		return thriftClass;
	}

	public static TBase getReusableInstance(Class<? extends TBase> thriftClass) {
		try {
			return thriftClass.newInstance();
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e.getCause());
		} catch (InstantiationException e) {
			throw new RuntimeException(e.getCause());
		}
	}

	@Deprecated
	public static TBase getThriftObject(Row row, Class<? extends TBase> thriftClass) {
		TBase tbase = null;
		int i = 0;

		if (row == null) {
			return tbase;
		}
		try {
			tbase = thriftClass.newInstance();
			Set<FieldValueMetaData> binarySet = (Set<FieldValueMetaData>)
				thriftClass.getField("binaryFieldValueMetaDatas").get(null);
			List<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries
				= ThriftRowTranslator.getSortedFields(thriftClass, null);

			for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
				if (getDefaultValue(entry.getValue().valueMetaData) == row.getField(i)) {
					i++;
					continue;
				}
				// for struct property, put a nested Row here
				if (entry.getValue().valueMetaData.isStruct() && row.getField(i) instanceof Row) {
					StructMetaData structMetaData = (StructMetaData) entry.getValue().valueMetaData;
					tbase.setFieldValue(
						entry.getKey(),
						getThriftObject((Row) row.getField(i), structMetaData.structClass));
				} else {
					Object val = null;
					try {
						val = rowValueToThriftValue(
							entry.getValue().valueMetaData,
							row.getField(i));
						if (binarySet.contains(entry.getValue().valueMetaData)) {
							tbase.setFieldValue(
								entry.getKey(),
								ByteBuffer.wrap(val.toString().getBytes()));
						} else {
							tbase.setFieldValue(entry.getKey(), val);
						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				i++;

			}
			LOG.debug("decoded {}th field of Row total {}", i, row.getArity());
			return tbase;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
