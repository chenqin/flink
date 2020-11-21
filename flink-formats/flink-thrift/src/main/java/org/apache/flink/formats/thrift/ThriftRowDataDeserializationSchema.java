package org.apache.flink.formats.thrift;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.data.RowData;

import org.apache.flink.table.types.logical.RowType;

import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;

import java.io.IOException;

public class ThriftRowDataDeserializationSchema implements DeserializationSchema<RowData> {
	private Boolean skipCorruptedMessage;
	/** TypeInformation of the produced {@link RowData}. **/
	private final TypeInformation<RowData> resultTypeInfo;
	private final transient TBase reuseInstance;
	private final TDeserializer deserializer;

	public ThriftRowDataDeserializationSchema(
		Boolean skipCorruptedMessage,
		TBase reuseInstance,
		TypeInformation<RowData> resultTypeInfo) {
		this.skipCorruptedMessage = skipCorruptedMessage;
		this.reuseInstance = reuseInstance;
		// TODO: refactor code on Row translate,
		// move traverse nested struct and convert logic consistent with other formats
		this.resultTypeInfo = resultTypeInfo;
		deserializer = new TDeserializer();
	}
	/**
	 * Deserializes the byte message.
	 *
	 * @param message The message, as a byte array.
	 * @return The deserialized message as an object (null if the message cannot be deserialized).
	 */
	@Override
	public RowData deserialize(byte[] message) throws IOException {
		try {
			deserializer.deserialize(reuseInstance, message);
		} catch (TException e) {
			if (skipCorruptedMessage ) {
				return null;
			} else {
				throw new IOException(e.getMessage());
			}
		}
		return ThriftRowTranslator.getRowData(reuseInstance);
	}

	/**
	 * Method to decide whether the element signals the end of the stream. If
	 * true is returned the element won't be emitted.
	 *
	 * @param nextElement The element to test for the end-of-stream signal.
	 * @return True, if the element signals end of stream, false otherwise.
	 */
	@Override
	public boolean isEndOfStream(RowData nextElement) {
		return false;
	}

	/**
	 * Gets the data type (as a {@link TypeInformation}) produced by this function or input format.
	 *
	 * @return The data type produced by this function or input format.
	 */
	@Override
	public TypeInformation<RowData> getProducedType() {
		return resultTypeInfo;
	}
}
