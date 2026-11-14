package cn.lineai.ui.tokens;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.model.ThemePalette;

/**
 * Owns the active token snapshot and publishes replacements on theme change.
 *
 * <p>A theme change swaps the whole snapshot instead of mutating colors, so views restyle in
 * place and never need to be recreated.</p>
 */
public final class ThemeController {

    private final Observable<Tokens> tokens;

    /**
     * Creates the controller with an initial palette; null falls back to the dark palette.
     *
     * @param initial palette used until the next {@link #apply}
     */
    public ThemeController(ThemePalette initial) {
        this.tokens = new Observable<>(Tokens.of(initial == null ? ThemePalette.dark() : initial));
    }

    /**
     * Returns the active snapshot.
     *
     * @return the token snapshot currently in effect
     */
    public Tokens current() {
        return tokens.value();
    }

    /**
     * Publishes a new snapshot. Null palettes are ignored so a failed load cannot blank the UI.
     *
     * @param palette palette to activate
     */
    public void apply(ThemePalette palette) {
        if (palette != null) {
            tokens.set(Tokens.of(palette));
        }
    }

    /**
     * Subscribes to snapshot replacements.
     *
     * @param observer notified with the current snapshot now and on every theme change
     * @return handle used to unsubscribe
     */
    public Cancellable observe(Observable.Observer<Tokens> observer) {
        return tokens.observe(observer);
    }
}
