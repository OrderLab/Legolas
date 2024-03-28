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
package edu.umich.order.legolas.datastax_3_1_4;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Update;
import java.io.Closeable;
import java.util.Random;

/**
 *
 */
public final class CassandraClient implements Closeable {
    private static final RetryPolicy noretry = new RetryPolicy() {
        @Override
        public void init(Cluster cluster) {
        }
        @Override
        public void close() {
        }
        @Override
        public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            return RetryDecision.rethrow();
        }
        @Override
        public RetryDecision onRequestError(Statement statement, ConsistencyLevel cl, DriverException e, int nbRetry) {
            return RetryDecision.rethrow();
        }
        @Override
        public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            return RetryDecision.rethrow();
        }
        @Override
        public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            return RetryDecision.rethrow();
        }
    };

    private static final class DedicatedPolicy extends RoundRobinPolicy {
        @Override
        public void onUp(Host host) {}
        @Override
        public void onAdd(Host host) {}
    };

    private final Session session;
    private final Cluster cluster;
    private final String host;
    private final int port;
    private final int timeout;

    public CassandraClient(final String host, final int port, final int timeout,
            final String keyspaceName) throws Exception {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        // assuming clientPort is CQL port
        cluster = Cluster.builder().addContactPoint(host).withPort(port)
                .withRetryPolicy(noretry).withLoadBalancingPolicy(new DedicatedPolicy())
                .withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.QUORUM))
                .withSocketOptions(new SocketOptions().setConnectTimeoutMillis(timeout)).build();
        if (keyspaceName == null)
            session = cluster.connect();
        else
            session = cluster.connect(keyspaceName);
    }

    public CassandraClient(final String host, final int port, final int timeout) throws Exception {
        this(host, port, timeout, null);
    }

    public final ResultSet createKeyspace(final String keyspaceName,
            final String replicationStrategy, final int replicationFactor) {
        final String query = "CREATE KEYSPACE " + keyspaceName +
                " WITH replication = {'class':'" + replicationStrategy +
                "', 'replication_factor':" + replicationFactor + "};";
        return session.execute(query);
    }

    public final ResultSet execute(final String cmd) {
        return session.execute(cmd);
    }

    public final ResultSet useKeyspace(final String keyspaceName) {
        final String query = "USE " + keyspaceName;
        return execute(query);
    }

    public final ResultSet read(final String fieldName, final String tableName,
            final String keyName, final Integer key) {
        final Select query = QueryBuilder.select().column(fieldName).from(tableName).where(
                QueryBuilder.eq(keyName, key)
        ).limit(1);
        return session.execute(query);
    }

    public final ResultSet update(final String tableName, final String fieldName,
            final String value, final String keyName, final Integer key) {
        final Update.Assignments query = QueryBuilder.update(tableName).where(
                QueryBuilder.eq(keyName, key)
        ).with(
                QueryBuilder.set(fieldName, value)
        );
        return session.execute(query);
    }

    @Override
    public final synchronized void close() {
        session.close();
        cluster.close();
    }
}
