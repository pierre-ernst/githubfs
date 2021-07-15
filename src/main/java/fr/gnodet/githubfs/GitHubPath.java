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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.github.pierre_ernst.githubfs.model.GitHubPackageUrl;

public class GitHubPath implements Path {

	private final GitHubFileSystem fileSystem;
	private final byte[] path;
	private volatile int[] offsets;
	private volatile int hash = 0;
	private volatile byte[] resolved = null;

	public GitHubPath(GitHubPackageUrl purl) {
		this (purl.canonicalizePath().getFileSystem(), purl.canonicalizePath().getSubPath());
	}
	
	public GitHubPath(GitHubFileSystem fileSystem, String path) {
		this(fileSystem, path.getBytes());
	}
	
	public GitHubPath(GitHubFileSystem fileSystem, byte[] path) {
		this(fileSystem, path, false);
	}

	public GitHubPath(GitHubFileSystem fileSystem, byte[] path, boolean normalized) {
		this.fileSystem = fileSystem;
		if (normalized) {
			this.path = path;
		} else {
			this.path = normalize(path);
		}
	}

	@Override
	public GitHubFileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return (path.length > 0) && (path[0] == '/');
	}

	@Override
	public GitHubPath getRoot() {
		if (isAbsolute()) {
			return new GitHubPath(fileSystem, new byte[] { this.path[0] });
		}
		return null;
	}

	@Override
	public GitHubPath getFileName() {
		initOffsets();
		int nbOffsets = offsets.length;
		if (nbOffsets == 0) {
			return null;
		}
		if (nbOffsets == 1 && path[0] != '/') {
			return this;
		}
		int offset = offsets[nbOffsets - 1];
		int length = path.length - offset;
		byte[] path = new byte[length];
		System.arraycopy(this.path, offset, path, 0, length);
		return new GitHubPath(fileSystem, path);
	}

	@Override
	public GitHubPath getParent() {
		initOffsets();
		int nbOffsets = offsets.length;
		if (nbOffsets == 0) {
			return null;
		}
		int length = offsets[nbOffsets - 1] - 1;
		if (length <= 0) {
			return getRoot();
		}
		byte[] path = new byte[length];
		System.arraycopy(this.path, 0, path, 0, length);
		return new GitHubPath(fileSystem, path);
	}

	@Override
	public int getNameCount() {
		initOffsets();
		return offsets.length;
	}

	@Override
	public GitHubPath getName(int index) {
		initOffsets();
		if (index < 0 || index >= offsets.length) {
			throw new IllegalArgumentException();
		}
		int offset = this.offsets[index];
		int length;
		if (index == offsets.length - 1) {
			length = path.length - offset;
		} else {
			length = offsets[index + 1] - offset - 1;
		}
		byte[] path = new byte[length];
		System.arraycopy(this.path, offset, path, 0, length);
		return new GitHubPath(fileSystem, path);

	}

	@Override
	public GitHubPath subpath(int beginIndex, int endIndex) {
		initOffsets();
		if ((beginIndex < 0) || (beginIndex >= this.offsets.length) || (endIndex > this.offsets.length)
				|| (beginIndex >= endIndex)) {
			throw new IllegalArgumentException();
		}
		int offset = this.offsets[beginIndex];
		int length;
		if (endIndex == this.offsets.length) {
			length = this.path.length - offset;
		} else {
			length = this.offsets[endIndex] - offset - 1;
		}
		byte[] path = new byte[length];
		System.arraycopy(this.path, offset, path, 0, length);
		return new GitHubPath(fileSystem, path);
	}

	@Override
	public boolean startsWith(Path other) {
		GitHubPath p1 = this;
		GitHubPath p2 = checkPath(other);
		if (p1.isAbsolute() != p2.isAbsolute() || p1.path.length < p2.path.length) {
			return false;
		}
		int length = p2.path.length;
		for (int idx = 0; idx < length; idx++) {
			if (p1.path[idx] != p2.path[idx]) {
				return false;
			}
		}
		return p1.path.length == p2.path.length || p2.path[length - 1] == '/' || p1.path[length] == '/';
	}

	@Override
	public boolean startsWith(String other) {
		return startsWith(getFileSystem().getPath(other));
	}

	@Override
	public boolean endsWith(Path other) {
		GitHubPath p1 = this;
		GitHubPath p2 = checkPath(other);
		int i1 = p1.path.length - 1;
		if (i1 > 0 && p1.path[i1] == '/') {
			i1--;
		}
		int i2 = p2.path.length - 1;
		if (i2 > 0 && p2.path[i2] == '/') {
			i2--;
		}
		if (i2 == -1) {
			return i1 == -1;
		}
		if ((p2.isAbsolute() && (!isAbsolute() || i2 != i1)) || (i1 < i2)) {
			return false;
		}
		for (; i2 >= 0; i1--) {
			if (p2.path[i2] != p1.path[i1]) {
				return false;
			}
			i2--;
		}
		return (p2.path[i2 + 1] == '/') || (i1 == -1) || (p1.path[i1] == '/');
	}

	@Override
	public boolean endsWith(String other) {
		return endsWith(getFileSystem().getPath(other));
	}

	@Override
	public GitHubPath normalize() {
		byte[] p = getResolved();
		if (p == this.path) {
			return this;
		}
		return new GitHubPath(fileSystem, p, true);
	}

	@Override
	public GitHubPath resolve(Path other) {
		GitHubPath p1 = this;
		GitHubPath p2 = checkPath(other);
		if (p2.isAbsolute()) {
			return p2;
		}
		byte[] result;
		if (p1.path[p1.path.length - 1] == '/') {
			result = new byte[p1.path.length + p2.path.length];
			System.arraycopy(p1.path, 0, result, 0, p1.path.length);
			System.arraycopy(p2.path, 0, result, p1.path.length, p2.path.length);
		} else {
			result = new byte[p1.path.length + 1 + p2.path.length];
			System.arraycopy(p1.path, 0, result, 0, p1.path.length);
			result[p1.path.length] = '/';
			System.arraycopy(p2.path, 0, result, p1.path.length + 1, p2.path.length);
		}
		return new GitHubPath(fileSystem, result);
	}

	@Override
	public GitHubPath resolve(String other) {
		return resolve(getFileSystem().getPath(other));
	}

	@Override
	public Path resolveSibling(Path other) {
		if (other == null) {
			throw new NullPointerException();
		}
		GitHubPath parent = getParent();
		return parent == null ? other : parent.resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		return resolveSibling(getFileSystem().getPath(other));
	}

	@Override
	public GitHubPath relativize(Path other) {
		GitHubPath p1 = this;
		GitHubPath p2 = checkPath(other);
		if (p2.equals(p1)) {
			return new GitHubPath(fileSystem, new byte[0], true);
		}
		if (p1.isAbsolute() != p2.isAbsolute()) {
			throw new IllegalArgumentException();
		}
		// Check how many segments are common
		int nbNames1 = p1.getNameCount();
		int nbNames2 = p2.getNameCount();
		int l = Math.min(nbNames1, nbNames2);
		int nbCommon = 0;
		while (nbCommon < l && equalsNameAt(p1, p2, nbCommon)) {
			nbCommon++;
		}
		int nbUp = nbNames1 - nbCommon;
		// Compute the resulting length
		int length = nbUp * 3 - 1;
		if (nbCommon < nbNames2) {
			length += p2.path.length - p2.offsets[nbCommon] + 1;
		}
		// Compute result
		byte[] result = new byte[length];
		int idx = 0;
		while (nbUp-- > 0) {
			result[idx++] = '.';
			result[idx++] = '.';
			if (idx < length) {
				result[idx++] = '/';
			}
		}
		// Copy remaining segments
		if (nbCommon < nbNames2) {
			System.arraycopy(p2.path, p2.offsets[nbCommon], result, idx, p2.path.length - p2.offsets[nbCommon]);
		}
		return new GitHubPath(fileSystem, result);
	}

	@Override
	public URI toUri() {
		// TODO
		return null;
	}

	@Override
	public GitHubPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		byte[] result = new byte[path.length + 1];
		result[0] = '/';
		System.arraycopy(path, 0, result, 1, path.length);
		return new GitHubPath(fileSystem, result, true);
	}

	@Override
	public GitHubPath toRealPath(LinkOption... options) throws IOException {
		GitHubPath absolute = new GitHubPath(fileSystem, getResolvedPath()).toAbsolutePath();
		fileSystem.provider().checkAccess(absolute);
		return absolute;
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {
			private int index = 0;

			public boolean hasNext() {
				return index < getNameCount();
			}

			public Path next() {
				if (index < getNameCount()) {
					GitHubPath name = getName(index);
					index++;
					return name;
				}
				throw new NoSuchElementException();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int compareTo(Path paramPath) {
		GitHubPath p1 = this;
		GitHubPath p2 = checkPath(paramPath);
		byte[] a1 = p1.path;
		byte[] a2 = p2.path;
		int l1 = a1.length;
		int l2 = a2.length;
		for (int i = 0, l = Math.min(l1, l2); i < l; i++) {
			int b1 = a1[i] & 0xFF;
			int b2 = a2[i] & 0xFF;
			if (b1 != b2) {
				return b1 - b2;
			}
		}
		return l1 - l2;
	}

	private GitHubPath checkPath(Path paramPath) {
		if (paramPath == null) {
			throw new NullPointerException();
		}
		if (!(paramPath instanceof GitHubPath)) {
			throw new ProviderMismatchException();
		}
		return (GitHubPath) paramPath;
	}

	@Override
	public int hashCode() {
		int h = hash;
		if (h == 0) {
			h = hash = Arrays.hashCode(path);
		}
		return h;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof GitHubPath && ((GitHubPath) obj).fileSystem == fileSystem
				&& compareTo((GitHubPath) obj) == 0;
	}

	@Override
	public String toString() {
		return new String(path, StandardCharsets.UTF_8);
	}

	private void initOffsets() {
		if (this.offsets == null) {
			int count = 0;
			int index = 0;
			while (index < path.length) {
				byte c = path[index++];
				if (c != '/') {
					count++;
					while (index < path.length && path[index] != '/') {
						index++;
					}
				}
			}
			int[] result = new int[count];
			count = 0;
			index = 0;
			while (index < path.length) {
				int m = path[index];
				if (m == '/') {
					index++;
				} else {
					result[count++] = index++;
					while (index < path.length && path[index] != '/') {
						index++;
					}
				}
			}
			synchronized (this) {
				if (offsets == null) {
					offsets = result;
				}
			}
		}
	}

	private byte[] getResolvedPath() {
		byte[] r = resolved;
		if (r == null) {
			r = resolved = isAbsolute() ? getResolved() : toAbsolutePath().getResolvedPath();
		}
		return r;
	}

	private byte[] normalize(byte[] path) {
		if (path.length == 0) {
			return path;
		}
		int i = 0;
		for (int j = 0; j < path.length; j++) {
			int k = path[j];
			if (k == '\\') {
				return normalize(path, j);
			}
			if ((k == '/') && (i == '/')) {
				return normalize(path, j - 1);
			}
			if (k == 0) {
				throw new InvalidPathException(new String(path, StandardCharsets.UTF_8),
						"Path: nul character not allowed");
			}
			i = k;
		}
		return path;
	}

	private byte[] normalize(byte[] path, int index) {
		byte[] arrayOfByte = new byte[path.length];
		int i = 0;
		while (i < index) {
			arrayOfByte[i] = path[i];
			i++;
		}
		int j = i;
		int k = 0;
		while (i < path.length) {
			int m = path[i++];
			if (m == '\\') {
				m = '/';
			}
			if ((m != '/') || (k != '/')) {
				if (m == 0) {
					throw new InvalidPathException(new String(path, StandardCharsets.UTF_8),
							"Path: nul character not allowed");
				}
				arrayOfByte[j++] = (byte) m;
				k = m;
			}
		}
		if ((j > 1) && (arrayOfByte[j - 1] == '/')) {
			j--;
		}
		return j == arrayOfByte.length ? arrayOfByte : Arrays.copyOf(arrayOfByte, j);
	}

	private byte[] getResolved() {
		if (path.length == 0) {
			return path;
		}
		for (byte c : path) {
			if (c == '.') {
				return doGetResolved(this);
			}
		}
		return path;
	}

	private static byte[] doGetResolved(GitHubPath p) {
		int nc = p.getNameCount();
		byte[] path = p.path;
		int[] offsets = p.offsets;
		byte[] to = new byte[path.length];
		int[] lastM = new int[nc];
		int lastMOff = -1;
		int m = 0;
		for (int i = 0; i < nc; i++) {
			int n = offsets[i];
			int len = (i == offsets.length - 1) ? (path.length - n) : (offsets[i + 1] - n - 1);
			if (len == 1 && path[n] == (byte) '.') {
				if (m == 0 && path[0] == '/') // absolute path
					to[m++] = '/';
				continue;
			}
			if (len == 2 && path[n] == '.' && path[n + 1] == '.') {
				if (lastMOff >= 0) {
					m = lastM[lastMOff--]; // retreat
					continue;
				}
				if (path[0] == '/') { // "/../xyz" skip
					if (m == 0)
						to[m++] = '/';
				} else { // "../xyz" -> "../xyz"
					if (m != 0 && to[m - 1] != '/')
						to[m++] = '/';
					while (len-- > 0)
						to[m++] = path[n++];
				}
				continue;
			}
			if (m == 0 && path[0] == '/' || // absolute path
					m != 0 && to[m - 1] != '/') { // not the first name
				to[m++] = '/';
			}
			lastM[++lastMOff] = m;
			while (len-- > 0)
				to[m++] = path[n++];
		}
		if (m > 1 && to[m - 1] == '/')
			m--;
		return (m == to.length) ? to : Arrays.copyOf(to, m);
	}

	private static boolean equalsNameAt(GitHubPath p1, GitHubPath p2, int index) {
		int beg1 = p1.offsets[index];
		int len1;
		if (index == p1.offsets.length - 1) {
			len1 = p1.path.length - beg1;
		} else {
			len1 = p1.offsets[index + 1] - beg1 - 1;
		}
		int beg2 = p2.offsets[index];
		int len2;
		if (index == p2.offsets.length - 1) {
			len2 = p2.path.length - beg2;
		} else {
			len2 = p2.offsets[index + 1] - beg2 - 1;
		}
		if (len1 != len2) {
			return false;
		}
		for (int n = 0; n < len1; n++) {
			if (p1.path[beg1 + n] != p2.path[beg2 + n]) {
				return false;
			}
		}
		return true;
	}

	public String getSubPath() {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<getNameCount(); i++) {
			sb.append(this.getName(i));
			sb.append("/");
		}
		return sb.toString();
	}
}
