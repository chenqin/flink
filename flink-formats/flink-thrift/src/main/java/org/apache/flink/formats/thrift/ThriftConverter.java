package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.types.Row;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ThriftConverter is helper function to convert a thrift object to Row instance.
 * It also extract schema and RowTypeInfo from thrift class
 */
public class ThriftConverter {

	private static final String ver = "0.2";

	/**
	 * translate thrift TType into blink Table Types.
	 * https://thrift-tutorial.readthedocs.io/en/latest/thrift-types.html#base-types
	 * @param metaData
	 * @return
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
				return Types.MAP(getType(mapMetaData.keyMetaData), getType(mapMetaData.valueMetaData));
			case TType.STRUCT:
				StructMetaData structMetaData = (StructMetaData) metaData;
				RowTypeInfo nest = getRowTypeInfo(structMetaData.structClass);
				return Types.ROW_NAMED(nest.getFieldNames(), nest.getFieldTypes());
			default:
				return Types.VOID;
		}
	}

	/**
	 * return possible nested type information of each columns in a row, sorted by thrift id.
	 *
	 * @param thriftClass
	 * @return
	 */
	public static RowTypeInfo getRowTypeInfo(Class<? extends TBase> thriftClass) {
		Map<? extends TFieldIdEnum, FieldMetaData>
			metaDataMap;

		try {
			metaDataMap =
				(Map<? extends TFieldIdEnum, FieldMetaData>) thriftClass.getField("metaDataMap")
					.get(null);
			TypeInformation[] types = new TypeInformation[metaDataMap.size()];
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		TypeInformation[] types = new TypeInformation[metaDataMap.size()];
		String[] fields = new String[metaDataMap.size()];
		int i = 0;

		ArrayList<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
			new ArrayList<Map.Entry<? extends TFieldIdEnum, FieldMetaData>>(metaDataMap.entrySet());

		// align field index with discreet thrift id
		entries.sort(new Comparator<Map.Entry<? extends TFieldIdEnum, FieldMetaData>>() {
			@Override
			public int compare(Map.Entry<? extends TFieldIdEnum, FieldMetaData> o1, Map.Entry<? extends TFieldIdEnum, FieldMetaData> o2) {
				return o1.getKey().getThriftFieldId() - o2.getKey().getThriftFieldId();
			}
		});

		for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
			fields[i] = entry.getValue().fieldName;
			types[i] = getType(entry.getValue().valueMetaData);
			i++;
		}

		return new RowTypeInfo(types, fields);
	}

	/**
	 * generate table schema from thrift class.
	 *
	 * @param thriftClass
	 * @return
	 */
	public static TableSchema getTableSchema(Class<? extends TBase> thriftClass) {
		RowTypeInfo type = getRowTypeInfo(thriftClass);
		TableSchema.Builder builder = TableSchema.builder();
		for (int i = 0; i < type.getFieldTypes().length; i++) {
			builder = builder.field(type.getFieldNames()[i], type.getFieldTypes()[i]);
		}
		return builder.build();
	}

	private static Object getPrimitiveValue(FieldValueMetaData valueMetaData, Object val, boolean failedOnMissingField) {
		switch (valueMetaData.type) {
			case TType.BOOL:
				return Boolean.valueOf(val.toString());
			case TType.BYTE:
				return Byte.valueOf(val.toString());
			case TType.DOUBLE:
				return Double.valueOf(val.toString());
			case TType.STRING:
				// thrift treat binary as java String
				if (val instanceof byte[]) {
					return new String((byte[]) val);
				} else {
					return val;
				}
			case TType.I64:
				return BigInteger.valueOf(Long.valueOf(val.toString()));
			case TType.I16:
				return Short.valueOf(val.toString());
			case TType.I32:
				return Integer.valueOf(val.toString());
			case TType.ENUM:
				return val == null ? "" : String.valueOf(val.toString());
			case TType.LIST:
				if (val == null) {
					return new LinkedList<>();
				}
				ListMetaData listMetaData = (ListMetaData) valueMetaData;
				List items = (List) val;
				List<Object> transform = new LinkedList<>();
				items.forEach(item -> {
					transform.add(getPrimitiveValue(listMetaData.elemMetaData, item, failedOnMissingField));
				});
				return transform;
			case TType.SET:
				if (val == null) {
					return new LinkedList<>();
				}
				SetMetaData setMetaData = (SetMetaData) valueMetaData;
				Set sets = (Set) val;
				List<Object> transformset = new LinkedList<>();
				sets.forEach(item -> {
					transformset.add(getPrimitiveValue(setMetaData.elemMetaData, item, failedOnMissingField));
				});
				return transformset;
			case TType.MAP:
				if (val == null) {
					return new HashMap();
				}
				MapMetaData mapMetaData = (MapMetaData) valueMetaData;
				Map maps = (Map) val;
				HashMap transferals = new HashMap();
				maps.keySet().forEach(key -> {
					transferals.put(getPrimitiveValue(mapMetaData.keyMetaData, key, failedOnMissingField),
						getPrimitiveValue(mapMetaData.valueMetaData, maps.get(key), failedOnMissingField));
				});
				return transferals;
			case TType.STRUCT:
				return getRow((TBase) val, failedOnMissingField);
			default:
				throw new RuntimeException("not primitive type");
		}
	}

	public static Row getRow(TBase tBase, boolean failedOnMissingField) {
		if (tBase == null) {
			return null;
		}
		Map<? extends TFieldIdEnum, FieldMetaData>
			metaDataMap;
		try {
			metaDataMap =
				(Map<? extends TFieldIdEnum, FieldMetaData>) tBase.getClass().getField("metaDataMap")
					.get(tBase);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// allocate row by thrift field size
		Row result = new Row(metaDataMap.size());
		int i = 0;

		ArrayList<Map.Entry<? extends TFieldIdEnum, FieldMetaData>> entries =
			new ArrayList<>(metaDataMap.entrySet());

		// align field index with discreet thrift id
		entries.sort(new Comparator<Map.Entry<? extends TFieldIdEnum, FieldMetaData>>() {
			@Override
			public int compare(Map.Entry<? extends TFieldIdEnum, FieldMetaData> o1, Map.Entry<? extends TFieldIdEnum, FieldMetaData> o2) {
				return o1.getKey().getThriftFieldId() - o2.getKey().getThriftFieldId();
			}
		});

		for (Map.Entry<? extends TFieldIdEnum, FieldMetaData> entry : entries) {
			// for struct property, put a nested Row here
			if (entry.getValue().valueMetaData.isStruct()) {
				StructMetaData structMetaData = (StructMetaData) entry.getValue().valueMetaData;
				result.setField(i, getRow((TBase) tBase.getFieldValue(entry.getKey()), failedOnMissingField));
			} else {
				// else put a object

				Object val = null;
				try {
					val = tBase.getFieldValue(entry.getKey());
				} catch (Exception e) {
					// by default TDeserializer swallow type difference silently
				}
				result.setField(i, getPrimitiveValue(entry.getValue().valueMetaData, val, failedOnMissingField));
			}
			i++;
		}
		return result;
	}
}

