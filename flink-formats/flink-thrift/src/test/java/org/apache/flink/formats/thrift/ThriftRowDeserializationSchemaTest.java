package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.flinkformats.flinkthrift.tests.Operation;
import org.apache.flink.flinkformats.flinkthrift.tests.Task;
import org.apache.flink.flinkformats.flinkthrift.tests.Work;
import org.apache.flink.types.Row;

import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;


/**
 * test ThriftRowDeserailizationSchema Row generation, RowTypeInfo extraction from thrift struct.
 */
public class ThriftRowDeserializationSchemaTest extends junit.framework.TestCase {

	@org.junit.Test
	public void testThriftDeserailzerTypeInfo() {
		ThriftRowDeserializationSchema schema = new ThriftRowDeserializationSchema(
			org.apache.flink.flinkformats.flinkthrift.tests.Work.class, false);
		RowTypeInfo typeInfo = (RowTypeInfo) schema.getProducedType();
		// num1 : thrift id 0 type should be int
		assertEquals(Types.INT, typeInfo.getTypeAt(0));
		// op : convert enum to string
		assertEquals(Types.STRING, typeInfo.getTypeAt(2));
		// dates : list of string
		assertEquals(Types.LIST(Types.STRING), typeInfo.getTypeAt(4));
		// tasks : set of struct
		assertEquals(Types.LIST(Types.ROW_NAMED(new String[]{
				"isurgent", "date", "workid", "taskid"
			}, Types.BOOLEAN, Types.STRING, Types.BIG_INT, Types.BIG_INT)),
			typeInfo.getTypeAt(5));
		// mapping : map of bigint to bigint
		assertEquals(Types.MAP(Types.BIG_INT, Types.BIG_INT),
			typeInfo.getTypeAt(6));
		// workid : bigint, sorted by thrift id
		assertEquals(Types.BIG_INT, typeInfo.getTypeAt(7));
		// codes : binary as string
		assertEquals(Types.STRING, typeInfo.getTypeAt(8));
	}

	@org.junit.Test
	public void testThriftDeserializerRow() throws TException, IOException {
		ThriftRowDeserializationSchema schema = new ThriftRowDeserializationSchema(
			org.apache.flink.flinkformats.flinkthrift.tests.Work.class, false);

		Work sample = new Work();
		sample.codes = ByteBuffer.wrap("foo bar".getBytes());
		sample.comment = "me love foo bar";
		sample.mapping = new HashMap<>();
		sample.mapping.put(1L, 2L);
		sample.tasks = new HashSet<Task>();
		sample.tasks.add(new Task());

		TSerializer ser = new TSerializer();
		byte[] payload = ser.serialize(sample);

		// expect result consistent with type and sequence of field in typeInfo
		Row result = schema.deserialize(payload);
		RowTypeInfo typeInfo = (RowTypeInfo) schema.getProducedType();

		// workid : row indexed same as typeInfo referred with default value
		assertEquals(BigInteger.ZERO, result.getField(typeInfo.getFieldIndex("workid")));
		// mapping : map indexed and ser/deser properly
		assertEquals(BigInteger.TWO, ((HashMap<BigInteger, BigInteger>) result.getField(typeInfo.getFieldIndex("mapping"))).get(BigInteger.ONE));
		// tasks : set of struct indexed and ser/deser to list of named row
		assertEquals(
			Arrays.asList(ThriftConverter.getRow(
				new org.apache.flink.flinkformats.flinkthrift.tests.Task(), false)),
			result.getField(typeInfo.getFieldIndex("tasks")));

		// codes : binary indexed and ser/deser as string
		assertEquals("foo bar", result.getField(typeInfo.getFieldIndex("codes")));

		// comment : string indexed and ser/deser as string
		assertEquals("me love foo bar", result.getField(typeInfo.getFieldIndex("comment")));
	}

	@org.junit.Test
	public void testInvalidThriftPayloadType() throws TException, IOException {
		byte[] payload = "foo bar".getBytes();
		ThriftRowDeserializationSchema schema = new ThriftRowDeserializationSchema(
			org.apache.flink.flinkformats.flinkthrift.tests.Work.class, false);

		try {
			schema.deserialize(payload);
			fail("should not ignore parse error");
		} catch (Exception ex) {
			// success
		}

		schema = new ThriftRowDeserializationSchema(
			org.apache.flink.flinkformats.flinkthrift.tests.Work.class, true);
		assertNull("ignore parse thrift error", schema.deserialize(payload));
	}

	@org.junit.Test
	public void testInvalidFieldThriftPayload() throws TException {
		TSerializer ser = new TSerializer();
		Work w = new Work();
		w.op = Operation.ADD;
		w.comment = "foo bar";
		byte[] payload = ser.serialize(w);

		ThriftRowDeserializationSchema schema = new ThriftRowDeserializationSchema(
			org.apache.flink.flinkformats.flinkthrift.tests.InvalidFieldWork.class, false);
		try {
			Row result = schema.deserialize(payload);
			// comment : deserailize as i64 default value
			assertEquals(result.getField(3), BigInteger.ZERO);
		} catch (Exception e) {
			fail("ignore parser failed on field");
		}
	}
}
