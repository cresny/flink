/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.streaming.util;

import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.runtime.fs.hdfs.HadoopFileSystem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Utility for copying from local file system to a HDFS {@link FileSystem}.
 */
public class HDFSCopyFromLocal {

	public static void copyFromLocal(final File localPath,
			final URI remotePath) throws Exception {
		// Do it in another Thread because HDFS can deadlock if being interrupted while copying
		String threadName = "HDFS Copy from " + localPath + " to " + remotePath;

		final Tuple1<Exception> asyncException = Tuple1.of(null);

		Thread copyThread = new Thread(threadName) {
			@Override
			public void run() {
				try {
					Configuration hadoopConf = HadoopFileSystem.getHadoopConfiguration();

					FileSystem fs = FileSystem.get(remotePath, hadoopConf);

					copyFromLocalFile(fs, localPath, checkInitialDirectory(fs,localPath,remotePath));

				} catch (Exception t) {
					asyncException.f0 = t;
				}
			}

		};

		copyThread.setDaemon(true);
		copyThread.start();
		copyThread.join();

		if (asyncException.f0 != null) {
			throw asyncException.f0;
		}
	}

	/**
	 * Ensure that target path terminates with a new directory to be created by fs. If remoteURI does not specify a new
	 * directory, append local directory name.
	 * @param fs
	 * @param localPath
	 * @param remoteURI
	 * @return
	 * @throws IOException
	 */
	protected static URI checkInitialDirectory(final FileSystem fs,final File localPath, final URI remoteURI) throws IOException {
		if (localPath.isDirectory()) {
			Path remotePath = new Path(remoteURI);
			if (fs.exists(remotePath)) {
				return new Path(remotePath,localPath.getName()).toUri();
			}
		}
		return remoteURI;
	}

	protected static void copyFromLocalFile(final FileSystem fs, final File localPath, final URI remotePath) throws Exception {
		if (localPath.isDirectory()) {
			for (File file : localPath.listFiles()) {
				copyFromLocalFile(fs, file, new Path(new Path(remotePath),file.getName()).toUri());
			}
		} else {
			fs.copyFromLocalFile(new Path(localPath.getAbsolutePath()),new Path(remotePath));
		}
	}

}
