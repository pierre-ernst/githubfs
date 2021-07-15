package com.github.pierre_ernst.githubfs.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import com.github.pierre_ernst.githubfs.model.ContentFactory;
import com.github.pierre_ernst.githubfs.model.GitHubPackageUrl;

import fr.gnodet.githubfs.GitHubFileSystem;
import fr.gnodet.githubfs.GitHubPath;

public class ModelTest {

	@Test
	public void testPurl() {
		try {
			GitHub apiClient = new GitHubBuilder().build();
			GHRepository repo = apiClient.getUser("gnodet").getRepository("githubfs");
			
			String expected = "pkg:github/gnodet/githubfs@refs%2Fheads%2Fmaster#src";

			assertEquals(expected, new GitHubPackageUrl(repo, "refs/heads/master", "src").toString());
			assertEquals(expected, new GitHubPackageUrl(repo, "refs/heads/master", "/src").toString());
			assertEquals(expected, new GitHubPackageUrl(repo, "refs/heads/master", "src/").toString());
			assertEquals(expected, new GitHubPackageUrl(repo, "refs/heads/master", "/src/").toString());

			assertEquals(expected.replace("#src", ""), new GitHubPackageUrl(repo, "refs/heads/master", "/").toString());
			assertEquals(expected.replace("#src", ""), new GitHubPackageUrl(repo, "refs/heads/master", "  ").toString());
			assertEquals(expected.replace("#src", ""), new GitHubPackageUrl(repo, "refs/heads/master").toString());
			assertEquals(expected.replace("#src", ""), new GitHubPackageUrl(repo).toString());
			
			assertEquals(expected.replace("#src", "#README.md"),
					new GitHubPackageUrl(repo, "refs/heads/master", "README.md").toString());

			assertEquals(expected.replace("#src", "#src/main"),
					new GitHubPackageUrl(repo, "refs/heads/master", "/src/main").toString());
			
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}
	
	@Test
	public void testRefs() {
		try {
			GitHub apiClient = new GitHubBuilder().build();
			GHRepository repo = apiClient.getUser("package-url").getRepository("packageurl-java");
			
			GitHubFileSystem fs = (GitHubFileSystem) Paths.get(URI.create("github:gnodet/githubfs!/")).getFileSystem();
			
			assertEquals("pkg:github/package-url/packageurl-java@refs%2Fheads%2Fmaster#pom.xml",ContentFactory.getContent(repo, "refs/heads/master", new GitHubPath(fs,"pom.xml")).canonicalize());
			assertEquals("pkg:github/package-url/packageurl-java@refs%2Fheads%2Fmaster#pom.xml",ContentFactory.getContent(repo, new GitHubPath(fs,"pom.xml")).canonicalize());
			assertEquals("pkg:github/package-url/packageurl-java@refs%2Ftags%2Fpackageurl-java-1.0.0#pom.xml",ContentFactory.getContent(repo, "refs/tags/packageurl-java-1.0.0", new GitHubPath(fs,"pom.xml")).canonicalize());
			assertEquals("pkg:github/package-url/packageurl-java@refs%2Fpull%2F2%2Fhead#pom.xml",ContentFactory.getContent(repo, "refs/pull/2/head",new GitHubPath(fs,"pom.xml")).canonicalize());
			
				
			try {
			 new GitHubPackageUrl(repo, "foo/bar");
			 fail("An exception should have been raised.");
			} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("foo/bar"))	;
			}
			
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			fail(ex.getMessage());
		}
	}
}
