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

package edu.umich.order.legolas.hd_3_2_2;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import java.io.IOException;

/**
 *
 */
public final class GrayHDFSClient implements AutoCloseable {
    private final FileSystem fs;

    private static final byte[] BLOCK;
    static {
        BLOCK = new byte[10000];
        for (int i = 0; i < BLOCK.length; i++) {
            BLOCK[i] = (byte)'a';
        }
    }

    public GrayHDFSClient(final String confDir) throws IOException {
        final Configuration conf = new Configuration();
        conf.addResource(new Path(confDir + "/core-site.xml"));
        conf.addResource(new Path(confDir + "/hdfs-site.xml"));
        // because of Maven
        conf.set("fs.hdfs.impl", DistributedFileSystem.class.getName());
        conf.set("fs.file.impl", LocalFileSystem.class.getName());
        // set HADOOP user
        System.setProperty("HADOOP_USER_NAME", "gray");
        System.setProperty("hadoop.home.dir", "/");
        // get the filesystem - HDFS
        fs = FileSystem.get(conf);
    }

    public void readFile(final String filePath) throws IOException {
        final Path path = new Path(filePath);
        if (!fs.exists(path)) {
            throw new IOException("file not exists");
        }
        final byte[] bytes = new byte[16];
        int numBytes = 0;
        final FSDataInputStream in = fs.open(path);
        while ((numBytes = in.read(bytes)) > 0) {
            // do nothing
        }
        in.close();
    }

    public void writeFile(final String filePath, final int len) throws IOException {
        final Path path = new Path(filePath);
        final FSDataOutputStream out = fs.create(path, true);
        for (int i = 0; i < len; i += BLOCK.length) {
            out.write(BLOCK, 0, Math.min(len - i, BLOCK.length));
        }
        out.close();
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }
}
