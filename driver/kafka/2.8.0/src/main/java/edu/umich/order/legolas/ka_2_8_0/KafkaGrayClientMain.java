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

package edu.umich.order.legolas.ka_2_8_0;

import edu.umich.order.legolas.common.api.ClientStubFactory;
import edu.umich.order.legolas.common.api.OrchestratorRemote;
import edu.umich.order.legolas.common.api.OrchestratorRemote.ClientFeedback;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaGrayClientMain {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaGrayClientMain.class);

    private static OrchestratorRemote stub = null;
    private static int clientId = -1;
    private static int creatorId = -1;
    private static int request;
    private static String topicName;
    private static final Properties properties = new Properties();

    private final static Random rand = new Random();

    private static int clientProgress = 0;
    private static long t0 = System.nanoTime();

    private static void reply(final String result, final long nano) {
        LOG.info("progress = {}, time = {}", ++clientProgress, System.nanoTime() - t0);
        if (stub != null) {
            try {
                stub.send(new ClientFeedback(clientId, result, System.nanoTime() - nano));
            } catch (final RemoteException e) {
                LOG.error("Fail to send the request result to orchestrator server", e);
                System.exit(0);
            }
        }
    }

    private static void create() {
        int progress = 0;
        int failure = 0;
        while (progress < request) {
            try (final Admin client = Admin.create(properties)) {
                while (progress < request) {
                    final long nano = System.nanoTime();
                    final NewTopic newTopic = new NewTopic(
                            "gray-" + creatorId + "-" + progress, 2, (short) 2);
                    final CreateTopicsResult result = client.createTopics(Collections.singleton(newTopic));
                    result.all().get();
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Kafka client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (final Exception ignored) {}
            }
        }
    }

    private static void produce() {
        int progress = 0;
        int failure = 0;
        while (progress < request) {
            try (final Producer<String, String> producer = new KafkaProducer<String, String>(properties)) {
                while (progress < request) {
                    final long nano = System.nanoTime();
                    producer.send(new ProducerRecord<String, String>(topicName,
                            Integer.toString(rand.nextInt(request)),
                            Integer.toString(rand.nextInt(request))));
                    reply("success", nano);
                    progress++;
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Kafka client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (final Exception ignored) {}
            }
        }
    }

    private static void consume() {
        int progress = 0;
        int failure = 0;
        while (progress < request) {
            try (final Consumer<String, String> consumer = new KafkaConsumer<String, String>(properties)) {
                consumer.subscribe(Collections.singleton(topicName));
                while (progress < request) {
                    final long nano = System.nanoTime();
                    final ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (final ConsumerRecord<String, String> record : records) {
                        reply("success", nano);
                        progress++;
                    }
                }
            } catch (final Exception e) {
                failure++;
                LOG.warn("Kafka client exception", e);
                if (failure > 3) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (final Exception ignored) {}
            }
        }
    }

    private static void run(final String[] args) {
        LOG.info("running command " + Arrays.toString(args));
        final String bootstrapServers = args[0];
        final String command = args[1];
        properties.put("bootstrap.servers", bootstrapServers);
        switch (command) {
            case "create":
                creatorId = Integer.parseInt(args[2]);
                request = Integer.parseInt(args[3]);
                create();
                break;
            case "produce":
                properties.put("key.serializer",
                        "org.apache.kafka.common.serialization.StringSerializer");
                properties.put("value.serializer",
                        "org.apache.kafka.common.serialization.StringSerializer");
                topicName = args[2];
                request = Integer.parseInt(args[3]);
                if (stub != null) {
                    try {
                        Thread.sleep(10000);
                    } catch (Exception ignored) { }
                }
                produce();
                break;
            case "consume":
                properties.put("key.deserializer",
                        "org.apache.kafka.common.serialization.StringDeserializer");
                properties.put("value.deserializer",
                        "org.apache.kafka.common.serialization.StringDeserializer");
                topicName = args[2];
                final String consumerGroupId = args[3];
                properties.put("group.id", consumerGroupId);
                request = Integer.parseInt(args[4]);
                consume();
                break;
            default:
                LOG.error("undefined command -- " + command);
        }
    }

    public static void main(final String[] args) {
        final String name = ManagementFactory.getRuntimeMXBean().getName();
        final long pid = Long.parseLong(name.substring(0, name.indexOf('@')));
        LOG.info("My process's pid is {}", pid);
        if (args.length == 1) {
            clientId = Integer.parseInt(args[0]);
            LOG.info("My client id is {}", clientId);
            stub = ClientStubFactory.getOrchestratorStub(1099);
            if (stub == null) {
                LOG.error("Failed to get a client for orchestrator server");
            } else {
                try {
                    final String[] command = stub.registerClient(clientId, pid);
                    run(command);
                } catch (final RemoteException e) {
                    LOG.error("Failed to register with the orchestrator server");
                }
            }
        } else {
            run(args);
        }
    }
}
