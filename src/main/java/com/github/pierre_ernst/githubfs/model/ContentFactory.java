package com.github.pierre_ernst.githubfs.model;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.HttpException;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import fr.gnodet.githubfs.GitHubPath;

public class ContentFactory {

	public static FileContent wrapFile(GHRepository repo, String revision, GitHubPath path) throws IOException {
		return wrapFile(repo, revision, path, null);
	}

	public static FileContent wrapFile(GHRepository repo, String revision, GitHubPath path, GHContent file)
			throws IOException {
		GitHubPackageUrl cacheKey = new GitHubPackageUrl(repo, revision, path.getSubPath());

		if (file == null) {
			file = getFileContent(repo, cacheKey.getRevision(), cacheKey.canonicalizePath());
		}

		return new FileContent(repo, cacheKey.getRevision(), path, file);
	}

	public static DirectoryContent wrapDirectory(GHRepository repo, String revision, GitHubPath path)
			throws IOException {
		GitHubPackageUrl cacheKey = new GitHubPackageUrl(repo, revision, path.getSubPath());

		return new DirectoryContent(repo, cacheKey.getRevision(), path,
				getDirectoryContent(repo, cacheKey.getRevision(), cacheKey.canonicalizePath()));
	}

	public static Content getContent(GHRepository repo, GitHubPath path) throws IOException {
		return getContent(repo, null, path);
	}

	public static Content getContent(GHRepository repo, String revision, GitHubPath path) throws IOException {

		GitHubPackageUrl cacheKey = new GitHubPackageUrl(repo, revision, path.getSubPath());

		Content c = null;
		try {
			GHContent file = getFileContent(repo, cacheKey.getRevision(), cacheKey.canonicalizePath());
			c = new FileContent(repo, cacheKey.getRevision(), path, file);
		} catch (IOException ex) {
			if (ex.getMessage().endsWith(" is a directory.")) {
				c = new DirectoryContent(repo, cacheKey.getRevision(), path,
						getDirectoryContent(repo, cacheKey.getRevision(), cacheKey.canonicalizePath()));
			} else {
				throw ex;
			}
		}

		Objects.requireNonNull(c);
		return c;
	}

	private static GHContent getFileContent(GHRepository repo, String revision, GitHubPath path) throws IOException {
		GHContent result = null;
		try {
			result = repo.getFileContent(path.toString(), revision);
		} catch (HttpException ex) {
			if (ex.getCause() instanceof MismatchedInputException) {
				throw new IOException(path + " is a directory.");
			} else {
				throw ex;
			}
		}
		return result;
	}

	private static List<GHContent> getDirectoryContent(GHRepository repo, String revision, GitHubPath path)
			throws IOException {
		List<GHContent> result = null;
		try {
			result = repo.getDirectoryContent(path.getSubPath(), revision);
		} catch (HttpException ex) {
			if (ex.getCause() instanceof MismatchedInputException) {
				throw new IOException(path + " is a file.");
			} else {
				throw ex;
			}
		}
		return result;
	}
}
