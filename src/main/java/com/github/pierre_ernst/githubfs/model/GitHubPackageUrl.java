package com.github.pierre_ernst.githubfs.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;

import fr.gnodet.githubfs.GitHubPath;

public class GitHubPackageUrl implements Comparable<GitHubPackageUrl>{

	private PackageURL purl;
	private String revision;
	private GitHubPath path=null;
	
	public GitHubPackageUrl(GHRepository repo) throws IOException  {
		this(repo, null,null);
	}
	
	public GitHubPackageUrl(GHRepository repo, String revision) throws IOException  {
		this(repo, revision,null);
	}
	
	public GitHubPackageUrl(GHRepository repo, String revision, String path) throws IOException  {
		Objects.requireNonNull(repo);
		
		if ((path == null) || path.isEmpty())  {
			path = "/";
		}
		
		
		String p = path.trim();
		if (p.startsWith("/")) {
			p = p.substring(1);
		}
		if (p.endsWith("/")) {
			p = p.substring(0,p.length());
		}
		
		List<String> refs = Arrays.asList(repo.getRefs()).stream().map(GHRef::getRef).collect(Collectors.toList());

		this.revision = refs.get(0); // default value
		if ((revision != null) && (!revision.isEmpty())) {
			if (refs.contains(revision)) {
			this.revision = revision;
			} else {
				throw new IllegalArgumentException("Ref '"+revision + "' not found in "+repo.getFullName());
			}
		}
		
		try {
			purl = new PackageURL("github", repo.getOwnerName(), repo.getName(), this.revision, null, p);
		} catch (MalformedPackageURLException ex) {
			throw new IOException(ex);
		}
		
	}
	
	public String getRevision() {
		return revision;
	}
	
	@Override
	public String toString() {
		return purl.canonicalize();
	}
	
	public GitHubPath canonicalizePath() {
		if (path == null) {
			path = new GitHubPath(this);
		}
		return path;
	}

	@Override
	public int hashCode() {
		return Objects.hash(purl);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GitHubPackageUrl other = (GitHubPackageUrl) obj;
		return Objects.equals(purl, other.purl);
	}

	@Override
	public int compareTo(GitHubPackageUrl other) {
		return this.purl.canonicalize().compareTo(other.purl.canonicalize());
	}
}
