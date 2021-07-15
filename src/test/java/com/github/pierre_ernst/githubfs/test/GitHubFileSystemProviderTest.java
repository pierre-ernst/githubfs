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
package com.github.pierre_ernst.githubfs.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class GitHubFileSystemProviderTest {

	@Test
	public void testPathsGetAndDirectoryStream() {
		try {
			Path root = Paths.get(URI.create("github:gnodet/githubfs!/"));
			SortedSet<String> expected = new TreeSet<>();
			expected.add("src");
			expected.add(".gitignore");
			expected.add("README.md");
			expected.add("pom.xml");

			SortedSet<String> actual = new TreeSet<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
				for (Path p : stream) {
					actual.add(p.toString());
				}
			}

			assertEquals(expected, actual);

		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}

	@Test
	public void testFileRead() {
		try {
			Path file = Paths.get(URI.create("github:gnodet/githubfs!/README.md"));
			long expected = (long) Math.ceil(1.24 * 1024); // 1.24 KB
			long actual = Files.readAllBytes(file).length;

			assertEquals(expected, actual);

		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}

	@Test
	public void testFileWrite() {
		try {
			Path file = Paths.get(URI.create("github:gnodet/githubfs!/README.md"));

			try {
				Files.writeString(file, "Pierre Ernst");
				fail("An exception should have been raised.");
			} catch (ReadOnlyFileSystemException expected) {
				// NO-OP
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}

	@Test
	public void testRecursiveDirectoryStream() {
		try {
			Path root = Paths.get(URI.create("github:gnodet/githubfs!/src"));
			SortedSet<String> expected = new TreeSet<>();
			expected.add("src/main/java/fr/gnodet/githubfs/GitHubFileSystem.java");
			expected.add("src/main/java/fr/gnodet/githubfs/GitHubFileSystemProvider.java");
			expected.add("src/main/java/fr/gnodet/githubfs/GitHubPath.java");
			expected.add("src/main/java/fr/gnodet/githubfs/json/JsonReader.java");
			expected.add("src/main/java/fr/gnodet/githubfs/json/JsonWriter.java");
			expected.add("src/test/java/fr/gnodet/githubfs/GitHubFileSystemProviderTest.java");
			expected.add("src/main/resources/META-INF/services/java.nio.file.spi.FileSystemProvider");

			SortedSet<String> actual = new TreeSet<>();
			try (Stream<Path> walk = Files.walk(root)) {
				walk.forEach(p -> System.out.println(p.getFileSystem().getClass().getName() + " " + p.toString())); // TODO
				// actual.addAll(
				// walk.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.toSet())
				// );
			}

			assertEquals(expected, actual);

		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}

}
