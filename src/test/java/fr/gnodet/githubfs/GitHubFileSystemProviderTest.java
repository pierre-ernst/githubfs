/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package fr.gnodet.githubfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

public class GitHubFileSystemProviderTest {

	@Test
	public void testPathsGetAndDirectoryStream() {
		try {
			Path root = Paths
					.get(URI.create("github:gnodet/githubfs?revision=6cf9c81aa9dea56e4b08fb3d199c1106cea0686d!/"));
			SortedSet<String> expected = new TreeSet<>();
			expected.add("src");
			expected.add(".gitignore");
			expected.add("README.md");
			expected.add("pom.xml");

			SortedSet<String> retrieved = new TreeSet<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
				for (Path p : stream) {
					retrieved.add(p.toString());
				}
			}

			assertEquals(expected, retrieved);

		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}

	@Test
	public void testFileRead() {
		try {
			Path file = Paths.get(
					URI.create("github:gnodet/githubfs?revision=6cf9c81aa9dea56e4b08fb3d199c1106cea0686d!/README.md"));
			long expected = (long) Math.ceil(1.24 * 1024); // 1.24 KB
			long read = Files.readAllBytes(file).length;

			assertEquals(expected, read);

		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}
}
