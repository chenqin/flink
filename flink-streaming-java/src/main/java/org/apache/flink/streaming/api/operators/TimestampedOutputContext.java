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
