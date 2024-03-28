/*
 *  @author Haoze Wu <haoze@jhu.edu>
 *
 *  The Legolas Project
 *
 *  Copyright (c) 2024, University of Michigan, EECS, OrderLab.
 *      All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.umich.order.legolas.fl_1_14_0;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class FlinkGrayStreamingClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkGrayStreamingClientMain.class);

    private static DataStream<String> getKafkaSource(final StreamExecutionEnvironment env,
            final String brokers, final String topic, final String flinkGroup) {
        final KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(brokers)
                .setTopics(topic)
                .setGroupId(flinkGroup)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setDeserializer(KafkaRecordDeserializationSchema.valueOnly(StringDeserializer.class))
                .build();
        return env.fromSource(source, WatermarkStrategy.noWatermarks(),
                "kafka source from " + topic);
    }

    private static DataStreamSink<String> addKafkaSink(
            final DataStream<String> events, final String brokers, final String topic) {
        return events.sinkTo(KafkaSink.<String>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<String>builder()
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .setTopic(topic)
                                .build())
                .build());
    }

    public static void run(final String[] args) throws Exception {
        final String brokers = args[0];
        final String sourceTopic = args[1];
        final String sinkTopic = args[2];
        final String flinkGroup = "flink-group";
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);
        final DataStream<Integer> src =
                getKafkaSource(env, brokers, sourceTopic, flinkGroup).map(Integer::parseInt);

        DataStream<Integer> filter1 =
                src.filter(
                                new FilterFunction<Integer>() {
                                    @Override
                                    public boolean filter(Integer value) throws Exception {
                                        return true;
                                    }
                                })
                        .keyBy(
                                new KeySelector<Integer, Integer>() {
                                    @Override
                                    public Integer getKey(Integer value) throws Exception {
                                        return value;
                                    }
                                });

        DataStream<Tuple2<Integer, Integer>> filter2 =
                src.map(
                                new MapFunction<Integer, Tuple2<Integer, Integer>>() {

                                    @Override
                                    public Tuple2<Integer, Integer> map(Integer value)
                                            throws Exception {
                                        return new Tuple2<>(value, value + 1);
                                    }
                                })
                        .rebalance()
                        .filter(
                                new FilterFunction<Tuple2<Integer, Integer>>() {

                                    @Override
                                    public boolean filter(Tuple2<Integer, Integer> value)
                                            throws Exception {
                                        return true;
                                    }
                                })
                        .disableChaining()
                        .keyBy(
                                new KeySelector<Tuple2<Integer, Integer>, Integer>() {

                                    @Override
                                    public Integer getKey(Tuple2<Integer, Integer> value)
                                            throws Exception {
                                        return value.f0;
                                    }
                                });

        DataStream<String> connected =
                filter1.connect(filter2)
                        .flatMap(
                                new CoFlatMapFunction<Integer, Tuple2<Integer, Integer>, String>() {

                                    @Override
                                    public void flatMap1(Integer value, Collector<String> out)
                                            throws Exception {
                                        out.collect(value.toString());
                                    }

                                    @Override
                                    public void flatMap2(
                                            Tuple2<Integer, Integer> value, Collector<String> out)
                                            throws Exception {
                                        out.collect(value.toString());
                                    }
                                });

        addKafkaSink(connected, brokers, sinkTopic);
        final long nano = System.nanoTime();
        final JobClient job = env.executeAsync("Flink Gray Streaming Workload");
        FlinkGrayClientMain.reply("success", nano);
    }
}
