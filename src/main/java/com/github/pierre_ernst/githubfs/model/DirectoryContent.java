package com.github.pierre_ernst.githubfs.model;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import fr.gnodet.githubfs.GitHubPath;

public class DirectoryContent extends Content {

	private Set<Path> value;

	DirectoryContent(GHRepository repo, String revision, GitHubPath path, List<GHContent> content) throws IOException {
		super(repo, revision, path);

		Objects.requireNonNull(content);
		value = new HashSet<>();
		for (GHContent child : content) {
			if (child.isFile()) {
				ContentFactory.wrapFile(repo, revision, (GitHubPath) path.getFileSystem().getPath(child.getPath()),
						child);
			} else {
				ContentFactory.wrapDirectory(repo, revision,
						(GitHubPath) path.getFileSystem().getPath(child.getPath()));
			}
			value.add(Paths.get(child.getPath()));
		}
	}

	@Override
	public String toString() {
		return "DirectoryContent [purl=" + purl + ", item count=" + value.size() + "]";
	}

	public DirectoryStream<Path> list() {
		return new DirectoryStream<Path>() {

			@Override
			public void close() throws IOException {
				// NO-OP
			}

			@Override
			public Iterator<Path> iterator() {
				return DirectoryContent.this.value.iterator();
			}
		};
	}

	@Override
	public BasicFileAttributes getAttributes() {
		return new BasicFileAttributes() {

			@Override
			public boolean isRegularFile() {
				return false;
			}

			@Override
			public boolean isDirectory() {
				return true;
			}

			@Override
			public long size() {
				return -1;
			}

			@Override
			public Object fileKey() {
				return DirectoryContent.this.purl;
			}

			@Override
			public boolean isSymbolicLink() {
				return false;
			}

			@Override
			public boolean isOther() {
				return false;
			}

			@Override
			public FileTime lastModifiedTime() {
				return null;
			}

			@Override
			public FileTime lastAccessTime() {
				return null;
			}

			@Override
			public FileTime creationTime() {
				return null;
			}
		};
	}
}
