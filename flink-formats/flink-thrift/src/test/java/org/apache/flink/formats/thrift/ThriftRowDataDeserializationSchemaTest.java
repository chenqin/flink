package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;

import org.apache.flink.types.RowKind;

import org.apache.thrift.TBase;
import org.apache.thrift.TSerializer;
import org.junit.Assert;
import org.junit.Test;

public class ThriftRowDataDeserializationSchemaTest {

	@Test
	public void testReadThriftClass() {
		Class obj = ThriftRowTranslator.getThriftClass(
			"org.apache.flink.formats.thrift.Work");
		Assert.assertTrue(obj == Work.class);
	}

	@Test
	public void testTBaseInstance() {
		Object o = ThriftRowTranslator.getReusableInstance(ThriftRowTranslator.getThriftClass(
			"org.apache.flink.formats.thrift.Work"));
		Assert.assertTrue(o instanceof TBase);
	}

	@Test
	public void testThriftRowDataDeserialize() throws Exception {
		Class<? extends TBase> obj = ThriftRowTranslator.getThriftClass(
			"org.apache.flink.formats.thrift.Work");
		Work o = (Work) ThriftRowTranslator.getReusableInstance(obj);
		o.num2 = 1;
		o.op = Operation.ADD;
		TableSchema schema = ThriftRowTranslator.getTableSchema(obj);
		TSerializer tSerializer = new TSerializer();
		byte[] val = tSerializer.serialize(o);
		ThriftRowDataDeserializationSchema deserializationSchema =
			new ThriftRowDataDeserializationSchema(false, o);
		RowData result = deserializationSchema.deserialize(val);
		Assert.assertTrue(result.getInt(0) == 0);
		Assert.assertTrue(result.getInt(1) == 1);
		Assert.assertTrue(result.getInt(2) == 1);
	}

	@Test
	public void testThriftRowDataSerialize() throws Exception {
		GenericRowData r = new GenericRowData(4);
		r.setField(0, 0);
		r.setField(1, 1);
		r.setField(2, 0);
		r.setField(3, "hi");
		final TableSchema schema = ThriftRowTranslator.getTableSchema(ThriftRowTranslator.getThriftClass(
			"org.apache.flink.formats.thrift.Work"));
		final RowType rowType = (RowType) schema.toRowDataType().getLogicalType();
		ThriftRowDataSerializationSchema serializationSchema =
			new ThriftRowDataSerializationSchema(false, ThriftRowTranslator.getThriftClass(
			"org.apache.flink.formats.thrift.Work"), rowType);

		serializationSchema.serialize(r);
	}
}
