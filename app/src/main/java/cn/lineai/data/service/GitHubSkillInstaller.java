package cn.lineai.data.service;

import cn.lineai.R;
import cn.lineai.resource.ResourceProvider;
import cn.lineai.security.SimpleHttpClient;
import cn.lineai.workspace.WorkspacePaths;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GitHubSkillInstaller {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int MAX_ZIP_BYTES = 20 * 1024 * 1024;
    private static final Pattern REPO = Pattern.compile(
            "(?i)^https?://(?:www\\.)?github\\.com/([^/]+)/([^/#?]+)(?:/(?:tree|blob)/([^/]+)(/[^?#]*)?)?(?:[?#].*)?$");
    private static final Pattern RAW = Pattern.compile(
            "(?i)^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)(/.*)?$");

    private final ResourceProvider resourceProvider;
    private final WorkspacePaths workspacePaths;
    private final SkillFileManager fileManager;

    public GitHubSkillInstaller(
            ResourceProvider resourceProvider,
            WorkspacePaths workspacePaths,
            SkillFileManager fileManager
    ) {
        this.resourceProvider = resourceProvider;
        this.workspacePaths = workspacePaths;
        this.fileManager = fileManager;
    }

    public File downloadToTemp(String rawUrl) throws Exception {
        String url = rawUrl == null ? "" : rawUrl.trim();
        Resolved resolved = resolve(url);
        if (resolved == null) {
            throw new IllegalArgumentException(resourceProvider.getString(R.string.skill_github_invalid_url));
        }
        File tempRoot = new File(workspacePaths.getLinecodeRoot(), "tmp/skills-github");
        File tempDir = fileManager.uniqueChild(tempRoot, fileManager.sanitizeFileName(resolved.suggestedName));
        tempDir.mkdirs();
        try {
            if (resolved.rawSkillMdUrl != null) {
                String body = SimpleHttpClient.get(resolved.rawSkillMdUrl, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
                if (body == null || body.trim().length() == 0) {
                    throw new IllegalArgumentException(resourceProvider.getString(R.string.skill_github_no_skill_md));
                }
                File skillMd = new File(tempDir, "SKILL.md");
                fileManager.writeUtf8(skillMd, body);
                return tempDir;
            }
            byte[] zipBytes = downloadZip(resolved.zipballUrl);
            unzipCodeload(zipBytes, tempDir);
            File skillMd = fileManager.findSkillMd(tempDir, 0);
            if (skillMd == null) {
                throw new IllegalArgumentException(resourceProvider.getString(R.string.skill_github_no_skill_md));
            }
            return skillMd.getParentFile();
        } catch (Exception e) {
            fileManager.deleteRecursive(tempDir);
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new Exception(resourceProvider.getString(R.string.skill_github_fetch_failed, e.getMessage()), e);
        }
    }

    static Resolved resolve(String url) {
        if (url == null || url.trim().length() == 0) {
            return null;
        }
        Matcher raw = RAW.matcher(url.trim());
        if (raw.matches()) {
            String owner = raw.group(1);
            String repo = stripGitSuffix(raw.group(2));
            String ref = raw.group(3);
            String path = raw.group(4) == null ? "" : raw.group(4);
            String rawUrl;
            if (path.length() == 0) {
                rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + ref + "/SKILL.md";
            } else {
                rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + ref + path;
            }
            return new Resolved(repo, rawUrl, null);
        }
        Matcher repoMatch = REPO.matcher(url.trim());
        if (!repoMatch.matches()) {
            return null;
        }
        String owner = repoMatch.group(1);
        String name = stripGitSuffix(repoMatch.group(2));
        String ref = repoMatch.group(3);
        String path = repoMatch.group(4) == null ? "" : repoMatch.group(4);
        if (ref != null && path.toLowerCase(Locale.ROOT).endsWith("skill.md")) {
            String rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + name + "/" + ref + path;
            return new Resolved(name, rawUrl, null);
        }
        String zipRef = (ref == null || ref.length() == 0) ? "main" : ref;
        String zipball = "https://codeload.github.com/" + owner + "/" + name + "/zip/refs/heads/" + zipRef;
        return new Resolved(name, null, zipball);
    }

    private static String stripGitSuffix(String value) {
        if (value != null && value.endsWith(".git")) {
            return value.substring(0, value.length() - 4);
        }
        return value == null ? "" : value;
    }

    private static byte[] downloadZip(String url) throws Exception {
        try {
            SimpleHttpClient.DownloadResult result =
                    SimpleHttpClient.download(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_ZIP_BYTES);
            if (result.bytes.length == 0) {
                throw new Exception("empty archive");
            }
            return result.bytes;
        } catch (Exception first) {
            if (url.contains("/refs/heads/main")) {
                return downloadZip(url.replace("/refs/heads/main", "/refs/heads/master"));
            }
            throw first;
        }
    }

    private static void unzipCodeload(byte[] zipBytes, File target) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.length() == 0 || name.contains("..")) {
                    continue;
                }
                int slash = name.indexOf('/');
                String relative = slash >= 0 ? name.substring(slash + 1) : name;
                if (relative.length() == 0) {
                    continue;
                }
                File out = new File(target, relative);
                String canonicalTarget = target.getCanonicalPath();
                String canonicalOut = out.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalTarget + File.separator)
                        && !canonicalOut.equals(canonicalTarget)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int read;
                    while ((read = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    static final class Resolved {
        final String suggestedName;
        final String rawSkillMdUrl;
        final String zipballUrl;

        Resolved(String suggestedName, String rawSkillMdUrl, String zipballUrl) {
            this.suggestedName = suggestedName == null || suggestedName.length() == 0
                    ? "skill" : suggestedName;
            this.rawSkillMdUrl = rawSkillMdUrl;
            this.zipballUrl = zipballUrl;
        }
    }
}
