package cn.lineai.data.service;

import org.junit.Assert;
import org.junit.Test;

public final class GitHubSkillInstallerTest {
    @Test
    public void resolvesRepoRootToZipball() {
        GitHubSkillInstaller.Resolved resolved = GitHubSkillInstaller.resolve("https://github.com/acme/demo-skill");
        Assert.assertNotNull(resolved);
        Assert.assertEquals("demo-skill", resolved.suggestedName);
        Assert.assertNull(resolved.rawSkillMdUrl);
        Assert.assertTrue(resolved.zipballUrl.contains("codeload.github.com/acme/demo-skill"));
    }

    @Test
    public void resolvesRawSkillMd() {
        GitHubSkillInstaller.Resolved resolved = GitHubSkillInstaller.resolve(
                "https://raw.githubusercontent.com/acme/demo/main/SKILL.md");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(
                "https://raw.githubusercontent.com/acme/demo/main/SKILL.md",
                resolved.rawSkillMdUrl);
    }

    @Test
    public void resolvesBlobSkillMdToRaw() {
        GitHubSkillInstaller.Resolved resolved = GitHubSkillInstaller.resolve(
                "https://github.com/acme/demo/blob/main/skills/foo/SKILL.md");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(
                "https://raw.githubusercontent.com/acme/demo/main/skills/foo/SKILL.md",
                resolved.rawSkillMdUrl);
    }

    @Test
    public void rejectsNonGithub() {
        Assert.assertNull(GitHubSkillInstaller.resolve("https://example.com/skill"));
        Assert.assertNull(GitHubSkillInstaller.resolve(""));
        Assert.assertNull(GitHubSkillInstaller.resolve(null));
    }
}
