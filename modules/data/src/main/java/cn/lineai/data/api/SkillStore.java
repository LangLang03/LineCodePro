package cn.lineai.data.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cn.lineai.core.model.Skill;

/** SKILL.md extensions stored on disk with an enable flag. */
public interface SkillStore {

    List<Skill> list();

    List<Skill> enabled();

    Skill save(Skill input);

    Skill importSkill(InputStream input, String displayName) throws IOException;

    void setEnabled(String id, boolean enabled);

    void delete(String id);
}
