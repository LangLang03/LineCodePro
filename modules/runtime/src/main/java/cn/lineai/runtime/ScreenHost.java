package cn.lineai.runtime;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.tokens.Tokens;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ScreenHost implements AutoCloseable {
    /** Legacy push motion: slide, 280 ms EaseOut. */
    private static final long ENTER_MS = 280L;
    /** Legacy pop motion: reversed slide, 220 ms EaseIn. */
    private static final long EXIT_MS = 220L;

    private final FrameLayout container;
    private final BackStack navigation;
    private final Function<ScreenKey<?, ?>, View> factory;
    private final Map<ScreenKey<?, ?>, View> views = new LinkedHashMap<>();
    private final Cancellable observation;
    private final java.util.List<View> exiting = new ArrayList<>();
    private ScreenKey<?, ?> lastTop;
    private int lastCount;
    private boolean suppressAnimation;
    private boolean skipNextAnimation;

    public ScreenHost(FrameLayout container, BackStack navigation, Function<ScreenKey<?, ?>, View> factory) {
        this.container = container;
        this.navigation = navigation;
        this.factory = factory;
        observation = navigation.observe(this::render);
    }

    private void render(List<ScreenKey<?, ?>> entries) {
        releaseInterruptedExits();
        ScreenKey<?, ?> previousTop = lastTop;
        int previousCount = lastCount;
        boolean skip = suppressAnimation || skipNextAnimation;
        skipNextAnimation = false;
        int width = container.getWidth() > 0 ? container.getWidth()
                : container.getResources().getDisplayMetrics().widthPixels;
        boolean overlay = entries.size() > 1;
        ScreenKey<?, ?> top = navigation.current();
        boolean push = overlay && previousCount < entries.size()
                && (previousTop == null || !previousTop.equals(top));
        boolean pop = overlay && previousTop != null && previousCount > entries.size();
        boolean animate = !skip && width > 0
                && (pop || (push && navigation.lastPushAnimated()));
        View leaving = pop && previousTop != null ? views.get(previousTop) : null;

        for (ScreenKey<?, ?> key : new ArrayList<>(views.keySet())) {
            if (!entries.contains(key)) {
                View removed = views.remove(key);
                if (removed == null) continue;
                if (animate && removed == leaving) {
                    animateExit(removed, width);
                } else {
                    dispose(removed);
                }
            }
        }
        container.setVisibility(overlay ? View.VISIBLE : View.GONE);
        if (!overlay) {
            for (View known : views.values()) {
                known.animate().cancel();
                known.setVisibility(View.GONE);
                known.setTranslationX(0f);
            }
            lastTop = null;
            lastCount = 0;
            return;
        }
        View view = views.get(top);
        if (view == null) {
            view = factory.apply(top);
            views.put(top, view);
            container.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        View previous = push && previousTop != null ? views.get(previousTop) : null;
        for (View known : views.values()) {
            if (known == view) continue;
            known.animate().cancel();
            if (previous != known) known.setVisibility(View.GONE);
        }
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
        if (!animate) {
            view.setTranslationX(0f);
            for (View known : views.values()) {
                if (known != view) {
                    known.setTranslationX(0f);
                    known.setVisibility(View.GONE);
                }
            }
        } else if (push) {
            // Forward: the new page slides in from the right while the previous page
            // exits to the left, matching the legacy 280 ms host animation.
            if (previous != null && previous != view) {
                previous.setVisibility(View.VISIBLE);
                previous.setTranslationX(0f);
                animateOut(previous, -width, ENTER_MS);
                view.bringToFront();
            }
            view.setTranslationX(width);
            animateIn(view, ENTER_MS);
        } else {
            // Back: the target page re-enters from the left while the current page
            // exits to the right, so back always reads left-to-right. The exiting view
            // was removed from the map above and disposes itself when the slide ends.
            view.setTranslationX(-width);
            animateIn(view, EXIT_MS);
        }
        lastTop = top;
        lastCount = entries.size();
        if (view instanceof cn.lineai.ui.kit.Refreshable) {
            ((cn.lineai.ui.kit.Refreshable) view).onShown();
        }
    }

    private void animateIn(View view, long duration) {
        view.animate().translationX(0f).setDuration(duration)
                .setInterpolator(duration == EXIT_MS
                        ? new android.view.animation.AccelerateInterpolator()
                        : new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void animateOut(View view, float target, long duration) {
        view.animate().translationX(target).setDuration(duration)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setTranslationX(0f);
                }).start();
    }

    private void animateExit(View view, int width) {
        exiting.add(view);
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.setTranslationX(0f);
        view.animate().translationX(width).setDuration(EXIT_MS)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    exiting.remove(view);
                    dispose(view);
                }).start();
    }

    /** Releases pages whose exit animation was cut short by a newer navigation. */
    private void releaseInterruptedExits() {
        for (View view : new ArrayList<>(exiting)) {
            view.animate().cancel();
            dispose(view);
        }
        exiting.clear();
    }

    public void applyTokens(Tokens tokens) {
        container.setBackgroundColor(tokens.bg);
        for (View view : views.values()) {
            if (view instanceof SettingsPage) ((SettingsPage) view).applyTokens(tokens);
        }
    }

    public void visit(java.util.function.Consumer<View> consumer) {
        for (View view : views.values()) consumer.accept(view);
    }

    public Bundle save() {
        ArrayList<Bundle> entries = new ArrayList<>();
        for (ScreenKey<?, ?> key : navigation.entries()) {
            Bundle entry = new Bundle();
            entry.putString("route", key.id().route());
            Bundle args = new Bundle();
            for (Map.Entry<String, Object> arg : key.args().asMap().entrySet()) {
                args.putString(arg.getKey(), (String) arg.getValue());
            }
            entry.putBundle("params", args);
            View view = views.get(key);
            if (view instanceof SettingsPage) entry.putInt("scroll", ((SettingsPage) view).scroll.getScrollY());
            entries.add(entry);
        }
        Bundle saved = new Bundle();
        saved.putParcelableArrayList("screens", entries);
        return saved;
    }

    public void restore(Bundle saved, ScreenRegistry registry) {
        if (saved == null) return;
        ArrayList<Bundle> entries = saved.getParcelableArrayList("screens");
        if (entries == null || entries.isEmpty()) return;
        List<Params.Args> restored = new ArrayList<>();
        for (Bundle entry : entries) {
            Map<String, String> args = new LinkedHashMap<>();
            Bundle params = entry.getBundle("params");
            for (String key : params.keySet()) args.put(key, params.getString(key));
            ScreenKey<?, ?> screen = registry.require(entry.getString("route")).restore(new Params.Args(args));
            restored.add(screen.save());
        }
        suppressAnimation = true;
        skipNextAnimation = true;
        navigation.restore(restored);
        suppressAnimation = false;
        for (int i = 0; i < entries.size(); i++) {
            ScreenKey<?, ?> key = navigation.entries().get(i);
            if (i == 0) continue;
            View view = views.get(key);
            if (view == null) {
                view = factory.apply(key);
                views.put(key, view);
                view.setVisibility(View.GONE);
                container.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            if (view instanceof SettingsPage) {
                SettingsPage page = (SettingsPage) view;
                int offset = entries.get(i).getInt("scroll", 0);
                page.post(() -> page.scroll.scrollTo(0, offset));
            }
        }
    }

    private void dispose(View view) {
        container.removeView(view);
        if (view instanceof SettingsPage) ((SettingsPage) view).dispose();
    }

    @Override public void close() {
        observation.cancel();
        releaseInterruptedExits();
        for (View view : views.values()) dispose(view);
        views.clear();
        navigation.close();
    }
}
