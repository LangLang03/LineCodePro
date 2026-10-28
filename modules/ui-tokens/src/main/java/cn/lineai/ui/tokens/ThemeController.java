package cn.lineai.ui.tokens;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.model.ThemePalette;

/** Owns the active token snapshot and publishes replacements on theme change. */
public final class ThemeController {

    private final Observable<Tokens> tokens;

    public ThemeController(ThemePalette initial) {
        this.tokens = new Observable<>(Tokens.of(initial == null ? ThemePalette.dark() : initial));
    }

    public Tokens current() {
        return tokens.value();
    }

    public void apply(ThemePalette palette) {
        if (palette != null) {
            tokens.set(Tokens.of(palette));
        }
    }

    public Cancellable observe(Observable.Observer<Tokens> observer) {
        return tokens.observe(observer);
    }
}
