package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;

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
}
