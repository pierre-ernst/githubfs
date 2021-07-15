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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import com.github.pierre_ernst.githubfs.model.ContentFactory;
import com.github.pierre_ernst.githubfs.model.DirectoryContent;
import com.github.pierre_ernst.githubfs.model.FileContent;
import com.github.pierre_ernst.githubfs.model.GitHubPackageUrl;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class GitHubFileSystem extends FileSystem {

	private final GitHubFileSystemProvider fileSystemProvider;
	private final String revision;
	private final GHRepository ghRepo;

	public GitHubFileSystem(GitHubFileSystemProvider fileSystemProvider, String repository, Map<String, ?> env)
			throws IOException {
		String userInfo;
		String query;
		int index = repository.indexOf('@');
		if (index >= 0) {
			userInfo = repository.substring(0, index);
			repository = repository.substring(index + 1);
		} else {
			userInfo = null;
		}
		index = repository.indexOf('?');
		if (index >= 0) {
			query = repository.substring(index + 1);
			repository = repository.substring(0, index);
		} else {
			query = null;
		}
		String revision = null;
		String login = null;
		String oauth = null;
		String password = null;
		if (env != null) {
			login = (String) env.get("login");
			oauth = (String) env.get("oauth");
			password = (String) env.get("password");
			revision = (String) env.get("revision");
		}
		if (query != null) {
			for (String pair : query.split("&")) {
				index = pair.indexOf("=");
				String key = URLDecoder.decode(pair.substring(0, index), "UTF-8");
				String val = URLDecoder.decode(pair.substring(index + 1), "UTF-8");
				switch (key) {
				case "revision":
					revision = val;
					break;
				case "login":
					login = val;
					break;
				case "oauth":
					oauth = val;
					break;
				case "password":
					password = val;
					break;
				}
			}
		}
		if (userInfo != null) {
			String[] infos = userInfo.split(":");
			login = infos[0];
			password = infos[1];
		}
		if (password == null && oauth == null) {
			Path p = Paths.get(System.getProperty("user.home"), ".github");
			if (Files.isRegularFile(p)) {
				Properties properties = new Properties();
				try (Reader r = Files.newBufferedReader(p, Charset.defaultCharset())) {
					properties.load(r);
				}
				String pLogin = properties.getProperty("login");
				String pPassword = properties.getProperty("password");
				String pOauth = properties.getProperty("oauth");
				if (login == null || login.equals(pLogin)) {
					login = pLogin;
					password = pPassword;
					oauth = pOauth;
				}
			}
		}

		GitHubBuilder ghBuilder = new GitHubBuilder();
		if (oauth != null) {
			ghBuilder.withOAuthToken(oauth);
		} else {
			if ((login != null) && (password != null)) {
				ghBuilder.withPassword(login, password);
			}
		}
		this.fileSystemProvider = fileSystemProvider;

		Path cacheDirectory = Path.of(System.getProperty("user.home"), ".okHttp");
		if (!Files.exists(cacheDirectory)) {
			Files.createDirectory(cacheDirectory);
		}
		Cache okCache = new Cache(cacheDirectory.toFile(), 10 * 1024 * 1024); // 10MB cache
		OkHttpClient okClient = new OkHttpClient.Builder().cache(okCache).build();
		ghBuilder.withConnector(new OkHttpConnector(okClient));

		ghBuilder.withRateLimitHandler(RateLimitHandler.FAIL);

		if ((System.getProperty("http.proxyHost") != null) && (System.getProperty("http.proxyPort") != null)) {
			ghBuilder.withProxy(new Proxy(Type.HTTP, new InetSocketAddress(System.getProperty("http.proxyHost"),
					Integer.parseInt(System.getProperty("http.proxyPort")))));
		}
		GitHub gitHub = ghBuilder.build();

		ghRepo = gitHub.getRepository(repository);

		this.revision = new GitHubPackageUrl(ghRepo, revision).getRevision();

	}

	@Override
	public FileSystemProvider provider() {
		return fileSystemProvider;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Collections.<Path>singleton(new GitHubPath(this, new byte[] { '/' }));
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return null;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return null;
	}

	@Override
	public Path getPath(String first, String... more) {
		String path;
		if (more.length == 0) {
			path = first;
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(first);
			for (String segment : more) {
				if (segment.length() > 0) {
					if (sb.length() > 0) {
						sb.append('/');
					}
					sb.append(segment);
				}
			}
			path = sb.toString();
		}
		return new GitHubPath(this, path.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		int colonIndex = syntaxAndPattern.indexOf(':');
		if (colonIndex <= 0 || colonIndex == syntaxAndPattern.length() - 1) {
			throw new IllegalArgumentException(
					"syntaxAndPattern must have form \"syntax:pattern\" but was \"" + syntaxAndPattern + "\"");
		}

		String syntax = syntaxAndPattern.substring(0, colonIndex);
		String pattern = syntaxAndPattern.substring(colonIndex + 1);
		String expr;
		switch (syntax) {
		case "glob":
			expr = globToRegex(pattern);
			break;
		case "regex":
			expr = pattern;
			break;
		default:
			throw new UnsupportedOperationException("Unsupported syntax \'" + syntax + "\'");
		}
		final Pattern regex = Pattern.compile(expr);
		return new PathMatcher() {
			@Override
			public boolean matches(Path path) {
				return regex.matcher(path.toString()).matches();
			}
		};
	}

	private String globToRegex(String pattern) {
		StringBuilder sb = new StringBuilder(pattern.length());
		int inGroup = 0;
		int inClass = 0;
		int firstIndexInClass = -1;
		char[] arr = pattern.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			char ch = arr[i];
			switch (ch) {
			case '\\':
				if (++i >= arr.length) {
					sb.append('\\');
				} else {
					char next = arr[i];
					switch (next) {
					case ',':
						// escape not needed
						break;
					case 'Q':
					case 'E':
						// extra escape needed
						sb.append('\\');
					default:
						sb.append('\\');
					}
					sb.append(next);
				}
				break;
			case '*':
				if (inClass == 0)
					sb.append(".*");
				else
					sb.append('*');
				break;
			case '?':
				if (inClass == 0)
					sb.append('.');
				else
					sb.append('?');
				break;
			case '[':
				inClass++;
				firstIndexInClass = i + 1;
				sb.append('[');
				break;
			case ']':
				inClass--;
				sb.append(']');
				break;
			case '.':
			case '(':
			case ')':
			case '+':
			case '|':
			case '^':
			case '$':
			case '@':
			case '%':
				if (inClass == 0 || (firstIndexInClass == i && ch == '^'))
					sb.append('\\');
				sb.append(ch);
				break;
			case '!':
				if (firstIndexInClass == i)
					sb.append('^');
				else
					sb.append('!');
				break;
			case '{':
				inGroup++;
				sb.append('(');
				break;
			case '}':
				inGroup--;
				sb.append(')');
				break;
			case ',':
				if (inGroup > 0)
					sb.append('|');
				else
					sb.append(',');
				break;
			default:
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	InputStream newInputStream(GitHubPath path) throws IOException {
		FileContent c = ContentFactory.wrapFile(ghRepo, revision, path);
		return c.getInputStream();
	}

	DirectoryStream<Path> newDirectoryStream(final GitHubPath dir, DirectoryStream.Filter<? super Path> filter)
			throws IOException {
		DirectoryContent c = ContentFactory.wrapDirectory(ghRepo, revision, dir);
		return c.list();
	}

	<A extends BasicFileAttributes> SeekableByteChannel newByteChannel(Path path) throws IOException {
		final byte[] data = newInputStream((GitHubPath) path).readAllBytes();

		return new SeekableByteChannel() {
			long position;

			@Override
			public int read(ByteBuffer dst) throws IOException {
				int l = (int) Math.min(dst.remaining(), size() - position);
				dst.put(data, (int) position, l);
				position += l;
				return l;
			}

			@Override
			public int write(ByteBuffer src) throws IOException {
				throw new ReadOnlyFileSystemException();
			}

			@Override
			public long position() throws IOException {
				return position;
			}

			@Override
			public SeekableByteChannel position(long newPosition) throws IOException {
				position = newPosition;
				return this;
			}

			@Override
			public long size() throws IOException {
				return data.length;
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean isOpen() {
				return true;
			}

			@Override
			public void close() throws IOException {
			}
		};
	}

	<A extends BasicFileAttributes> A readAttributes(Path path, Class<A> clazz) throws IOException {
		if (clazz != BasicFileAttributes.class) {
			throw new UnsupportedOperationException();
		}

		return (A) ContentFactory.getContent(ghRepo, revision, (GitHubPath) path).getAttributes();
	}
}
