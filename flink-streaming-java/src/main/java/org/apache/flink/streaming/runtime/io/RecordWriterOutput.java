/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.io;

import java.io.IOException;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.runtime.event.AbstractEvent;
import org.apache.flink.runtime.io.network.api.writer.RecordWriter;
import org.apache.flink.runtime.plugable.SerializationDelegate;
import org.apache.flink.streaming.api.operators.Output;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.MultiplexingStreamRecordSerializer;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecordSerializer;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Implementation of {@link Output} that sends data using a {@link RecordWriter}.
 */
@Internal
public class RecordWriterOutput<OUT> implements Output<StreamRecord<OUT>> {

	private StreamRecordWriter<SerializationDelegate<StreamElement>> recordWriter;
	
	private SerializationDelegate<StreamElement> outSerializationDelegate;

	private boolean isSideOutput;

	@SuppressWarnings("unchecked")
	public RecordWriterOutput(
			StreamRecordWriter<SerializationDelegate<StreamRecord<OUT>>> recordWriter,
			TypeSerializer<?> outSerializer,
			boolean enableWatermarkMultiplexing,
			boolean isSideOutput) {

		checkNotNull(recordWriter);
		this.isSideOutput = isSideOutput;

		// generic hack: cast the writer to generic Object type so we can use it 
		// with multiplexed records and watermarks
		this.recordWriter = (StreamRecordWriter<SerializationDelegate<StreamElement>>) 
				(StreamRecordWriter<?>) recordWriter;

		TypeSerializer<StreamElement> outRecordSerializer;
		if (enableWatermarkMultiplexing) {
			outRecordSerializer = new MultiplexingStreamRecordSerializer<>(outSerializer);
		} else {
			outRecordSerializer = (TypeSerializer<StreamElement>)
					(TypeSerializer<?>) new StreamRecordSerializer<>(outSerializer);
		}

		if (outSerializer != null) {
			outSerializationDelegate = new SerializationDelegate<StreamElement>(outRecordSerializer);
		}
	}

	@Override
	public void collect(StreamRecord<OUT> record) {
		try {
			if(!this.isSideOutput) {
				outSerializationDelegate.setInstance(record);
				recordWriter.emit(outSerializationDelegate);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public void sideCollect(StreamRecord element) {
		try{
			//hack, filter out exception message to single output type downstream
			//abstractstreamoperator should contains more than one output
			if(this.isSideOutput) {
				outSerializationDelegate.setInstance(element);
				recordWriter.emit(outSerializationDelegate);
			}
		}
		catch (Exception e){
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Override
	public void emitWatermark(Watermark mark) {
		outSerializationDelegate.setInstance(mark);
		
		try {
			recordWriter.broadcastEmit(outSerializationDelegate);
		}
		catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public void broadcastEvent(AbstractEvent barrier) throws IOException, InterruptedException {
		recordWriter.broadcastEvent(barrier);
	}
	
	
	public void flush() throws IOException {
		recordWriter.flush();
	}
	
	@Override
	public void close() {
		recordWriter.close();
	}

	public void clearBuffers() {
		recordWriter.clearBuffers();
	}
}
