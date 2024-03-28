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

import java.util.ArrayList;
import java.util.List;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.runtime.operators.CheckpointCommitter;
import org.apache.flink.streaming.runtime.operators.GenericWriteAheadSink;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlinkGrayBatchClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkGrayBatchClientMain.class);

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
        final String textFilePath = args[1];
        final String kafkaTopic = args[2];
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.BATCH);
        final DataStream<String> text = env.readTextFile(textFilePath);
        final DataStream<Tuple2<String, Integer>> counts =
                text.flatMap(new Tokenizer()).keyBy(value -> value.f0).sum(1);
        // 170 words in the paragraph in Flink repo
        addKafkaSink(counts.map(String::valueOf), brokers, kafkaTopic);
        final long nano = System.nanoTime();
        env.execute("Streaming WordCount");
        FlinkGrayClientMain.reply("success", nano);
    }

    private static final class Tokenizer
            implements FlatMapFunction<String, Tuple2<String, Integer>> {
        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            for (final String token : value.toLowerCase().split("\\W+")) {
                if (token.length() > 0) {
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
    }

    private static class WordCountSink extends GenericWriteAheadSink<Tuple2<String, Integer>> {
        private static final long serialVersionUID = 1L;

        public List<String> words = new ArrayList<>();
        public List<Integer> counts = new ArrayList<>();

        public WordCountSink() throws Exception {
            super(new SimpleCommitter(),
                    TypeExtractor.getForObject(new Tuple2<>("", 1))
                            .createSerializer(new ExecutionConfig()),
                    "word count streaming sink");
        }

        @Override
        protected boolean sendValues(Iterable<Tuple2<String, Integer>> values, long checkpointId, long timestamp) {
            for (final Tuple2<String, Integer> value : values) {
                words.add(value.f0);
                counts.add(value.f1);
                FlinkGrayClientMain.reply("success", System.nanoTime());
            }
            return true;
        }
    }

    private static class SimpleCommitter extends CheckpointCommitter {
        private static final long serialVersionUID = 1L;

        private List<Tuple2<Long, Integer>> checkpoints;

        @Override
        public void open() throws Exception {}

        @Override
        public void close() throws Exception {}

        @Override
        public void createResource() throws Exception {
            checkpoints = new ArrayList<>();
        }

        @Override
        public void commitCheckpoint(int subtaskIdx, long checkpointID) {
            checkpoints.add(new Tuple2<>(checkpointID, subtaskIdx));
        }

        @Override
        public boolean isCheckpointCommitted(int subtaskIdx, long checkpointID) {
            return checkpoints.contains(new Tuple2<>(checkpointID, subtaskIdx));
        }
    }
}