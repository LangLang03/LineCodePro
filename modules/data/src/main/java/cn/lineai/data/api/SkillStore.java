package cn.lineai.data.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cn.lineai.core.model.Skill;

/**
 * SKILL.md extensions stored on disk with an enable flag.
 *
 * <p>The markdown files live under one root directory; the enabled state is kept in settings so
 * the files remain plain and portable. Implementations are synchronous; callers own the
 * scheduler.</p>
 */
public interface SkillStore {

    /**
     * Lists the installed skills, alphabetical by display name.
     *
     * @return every installed skill regardless of enabled state
     */
    List<Skill> list();

    /**
     * Lists only the enabled skills, in the same order as {@link #list()}.
     *
     * @return skills whose enabled flag is set
     */
    List<Skill> enabled();

    /**
     * Writes one skill as a SKILL.md file, creating or reusing its directory.
     *
     * @param input skill to persist; a blank name is rejected
     * @return the stored form of the skill, parsed back from the written file
     */
    Skill save(Skill input);

    /**
     * Imports a skill package from a stream.
     *
     * @param input stream holding a ZIP archive or a single SKILL.md document
     * @param displayName original file name; the extension selects the import format
     * @return the imported skill, installed and enabled
     * @throws IOException when the payload is not a supported format, contains no usable
     *         SKILL.md, or cannot be extracted into the skill root
     */
    Skill importSkill(InputStream input, String displayName) throws IOException;

    /**
     * Toggles the enabled flag without rewriting the files.
     *
     * @param id skill identifier; unknown identifiers are ignored
     * @param enabled whether the skill is offered to the model
     */
    void setEnabled(String id, boolean enabled);

    /**
     * Deletes the skill directory and clears its disabled entry.
     *
     * @param id skill identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
