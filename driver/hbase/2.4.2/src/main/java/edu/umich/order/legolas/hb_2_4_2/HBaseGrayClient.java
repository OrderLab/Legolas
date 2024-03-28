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
package edu.umich.order.legolas.hb_2_4_2;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;

/**
 *
 */
public class HBaseGrayClient implements AutoCloseable {
    private final Connection connection;

    public HBaseGrayClient() throws IOException {
        final Configuration config = HBaseConfiguration.create();
        // The default configuration can surprisingly find out the correct ports
        connection = ConnectionFactory.createConnection(config);
    }

    public void createTable(final int tableId, final int cfn) throws IOException {
        try (final Admin admin = connection.getAdmin()) {
            final TableName tableName = TableName.valueOf("t" + tableId);
            final TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder
                    .newBuilder(tableName);
            final List<ColumnFamilyDescriptor> cfs = new LinkedList<>();
            for (int i = 0; i < cfn; i++) {
                cfs.add(ColumnFamilyDescriptorBuilder.of("cf" + i));
            }
            tableDescriptorBuilder.setColumnFamilies(cfs);
            admin.createTable(tableDescriptorBuilder.build());
        }
    }

    public Result get(final int tableId, final int row) throws IOException {
        try (final Table table = connection.getTable(TableName.valueOf("t" + tableId))) {
            final Get g = new Get(Bytes.toBytes("row" + row));
            return table.get(g);
        }
    }

    public void put(final int tableId, final int cf, final int column, final int row,
            final String data) throws IOException {
        try (final Table table = connection.getTable(TableName.valueOf("t" + tableId))) {
            final Put p = new Put(Bytes.toBytes("row" + row));
            p.addColumn(Bytes.toBytes("cf" + cf), Bytes.toBytes("c" + column), Bytes.toBytes(data));
            table.put(p);
        }
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
