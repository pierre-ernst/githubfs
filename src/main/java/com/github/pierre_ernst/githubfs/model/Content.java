package com.github.pierre_ernst.githubfs.model;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import org.kohsuke.github.GHRepository;

import fr.gnodet.githubfs.GitHubPath;

public abstract class Content implements Comparable<Content> {

	protected GHRepository repo;
	protected String revision;
	protected GitHubPath path;
	protected GitHubPackageUrl purl;

	protected Content(GHRepository repo, String revision, GitHubPath path) throws IOException {
		Objects.requireNonNull(repo);
		this.repo = repo;

		Objects.requireNonNull(path);
		this.path = path;

		purl = new GitHubPackageUrl(repo, revision, path.getSubPath());

		this.revision = purl.getRevision();
	}

	public GHRepository getRepository() {
		return repo;
	}

	public String getRevision() {
		return revision;
	}

	public Path getPath() {
		return purl.canonicalizePath();
	}

	public String canonicalize() {
		return purl.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, repo, revision);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Content other = (Content) obj;
		return Objects.equals(path, other.path) && Objects.equals(repo, other.repo)
				&& Objects.equals(revision, other.revision);
	}

	@Override
	public int compareTo(Content other) {
		if (this.repo.equals(other.repo)) {
			if (this.revision.equals(other.revision)) {
				return this.path.toString().compareTo(other.path.toString());
			} else {
				return this.revision.compareTo(other.revision);
			}
		} else {
			return this.repo.getFullName().compareTo(other.repo.getFullName());
		}
	}

	public abstract BasicFileAttributes getAttributes();

}
