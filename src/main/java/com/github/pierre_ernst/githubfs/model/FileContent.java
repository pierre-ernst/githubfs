package com.github.pierre_ernst.githubfs.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import fr.gnodet.githubfs.GitHubPath;

public class FileContent extends Content {

	private GHContent value;

	FileContent(GHRepository repo, String revision, GitHubPath path, GHContent value) throws IOException {
		super(repo, revision, path);

		this.value = Objects.requireNonNull(value);
		if (!value.isFile()) {
			throw new IllegalArgumentException(value.getName() + " is not a file.");
		}
	}

	public InputStream getInputStream() throws IOException {
		return value.read();
	}

	@Override
	public String toString() {
		return "FileContent [purl=" + purl + ", size=" + (value.getSize() / 1024L) + " KB ]";
	}

	@Override
	public BasicFileAttributes getAttributes() {
		return new BasicFileAttributes() {

			@Override
			public boolean isRegularFile() {
				return true;
			}

			@Override
			public boolean isDirectory() {
				return false;
			}

			@Override
			public long size() {
				return value.getSize();
			}

			@Override
			public Object fileKey() {
				return FileContent.this.purl;
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
