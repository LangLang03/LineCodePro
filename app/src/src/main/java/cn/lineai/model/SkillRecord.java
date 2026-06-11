package cn.lineai.model;

public final class SkillRecord {
    public static final String LOCATION_APP = "app";
    public static final String LOCATION_PROJECT = "project";
    public static final String LOCATION_SSH = "ssh";

    private final String id;
    private final String name;
    private final String description;
    private final String rootPath;
    private final String skillMdPath;
    private final String location;
    private final boolean enabled;
    private final long discoveredAt;
    private final long updatedAt;

    public SkillRecord(
            String id,
            String name,
            String description,
            String rootPath,
            String skillMdPath,
            String location,
            boolean enabled,
            long discoveredAt,
            long updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.rootPath = rootPath == null ? "" : rootPath;
        this.skillMdPath = skillMdPath == null ? "" : skillMdPath;
        this.location = normalizeLocation(location);
        this.enabled = enabled;
        this.discoveredAt = discoveredAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRootPath() {
        return rootPath;
    }

    public String getSkillMdPath() {
        return skillMdPath;
    }

    public String getLocation() {
        return location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public String getLocationLabel() {
        if (LOCATION_PROJECT.equals(location)) {
            return "当前工作区 .linecode/skills";
        }
        if (LOCATION_SSH.equals(location)) {
            return "SSH ~/.linecode/skills";
        }
        return "应用 .linecode/skills";
    }

    public static String normalizeLocation(String value) {
        if (LOCATION_PROJECT.equals(value)) {
            return LOCATION_PROJECT;
        }
        if (LOCATION_SSH.equals(value)) {
            return LOCATION_SSH;
        }
        return LOCATION_APP;
    }
}
