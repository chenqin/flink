package org.apache.flink.api.common.functions;


public interface FlatMapFunction2<IN, OUT> extends FlatMapFunction<IN, OUT>{
	void flatMap(IN input, OutputContext<OUT> context);
}
