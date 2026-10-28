package cn.lineai.core.mvi.nav;

import cn.lineai.core.mvi.present.UiState;
import cn.lineai.core.mvi.testing.TestScheduler;

final class NavigationFixture {
    final TestScheduler scheduler = new TestScheduler();
    final ScreenId<State, Params.Empty> root = new ScreenId<>(
            "settings", null, true, Params.Empty::fromArgs);
    final ScreenId<State, PageParams> edit = new ScreenId<>(
            "settings.edit", root, true, PageParams::fromArgs);
    final ScreenId<State, Params.Empty> other = new ScreenId<>(
            "settings.about", root, false, Params.Empty::fromArgs);
    final ScreenId<State, Params.Empty> absent = new ScreenId<>(
            "settings.unopened", Params.Empty::fromArgs);
    final ScreenRegistry registry = new ScreenRegistry()
            .register(root).register(edit).register(other).register(absent);
    final BackStack stack = new BackStack(registry, scheduler);

    ScreenKey<State, PageParams> edit(String section) {
        return edit.key(new PageParams(section));
    }

    void seed() {
        stack.push(root.key(Params.Empty.INSTANCE));
        stack.push(edit("input"));
        stack.push(other.key(Params.Empty.INSTANCE));
    }

    static final class State implements UiState { }

    static final class PageParams implements Params {
        final String section;

        PageParams(String section) {
            this.section = section;
        }

        static PageParams fromArgs(Args args) {
            return new PageParams(args.requireString("section"));
        }

        @Override
        public Args toArgs() {
            return Args.of("section", section);
        }
    }
}
