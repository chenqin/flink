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

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.functions.OutputContext;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.util.Collector;

@Internal
public class TimestampedOutputContext<OUT> implements OutputContext<OUT>{
	protected Collector<OUT> collector;
	private Output<StreamRecord<OUT>> output;
	private StreamRecord reuse = null;

	public TimestampedOutputContext(Output<StreamRecord<OUT>> output){
		this.output = output;
		reuse = new StreamRecord(null);
	}

	public void setOutput(Output<StreamRecord<OUT>> output){
		this.output = output;
	}

	@Override
	public void collect(OUT element) {
		output.collect(reuse.replace(element));
	}

	@Override
	public <W> void sideCollect(W element) {
		output.sideCollect(reuse.replace(element));
	}

	@Override
	public void setTimeStamp(long timeStamp) {
		if(timeStamp == -1){
			reuse.eraseTimestamp();
		} else{
			reuse.setTimestamp(timeStamp);
		}
	}

	@Override
	public void emitWatermark(long watermarkts) {
		output.emitWatermark(new Watermark(watermarkts));
	}

	@Override
	public void close() {
		output.close();
	}
}
