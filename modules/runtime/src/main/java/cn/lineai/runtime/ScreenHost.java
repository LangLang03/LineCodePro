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
    private final FrameLayout container;
    private final BackStack navigation;
    private final Function<ScreenKey<?, ?>, View> factory;
    private final Map<ScreenKey<?, ?>, View> views = new LinkedHashMap<>();
    private final Cancellable observation;
    private ScreenKey<?, ?> lastTop;
    private int lastCount;
    private boolean suppressAnimation;

    public ScreenHost(FrameLayout container, BackStack navigation, Function<ScreenKey<?, ?>, View> factory) {
        this.container = container;
        this.navigation = navigation;
        this.factory = factory;
        observation = navigation.observe(this::render);
    }

    private void render(List<ScreenKey<?, ?>> entries) {
        ScreenKey<?, ?> previousTop = lastTop;
        int previousCount = lastCount;
        for (ScreenKey<?, ?> key : new ArrayList<>(views.keySet())) {
            if (!entries.contains(key)) dispose(views.remove(key));
        }
        ScreenKey<?, ?> top = navigation.current();
        for (View view : views.values()) view.setVisibility(View.GONE);
        boolean overlay = entries.size() > 1;
        container.setVisibility(overlay ? View.VISIBLE : View.GONE);
        if (!overlay) {
            lastTop = null;
            lastCount = 0;
            return;
        }
        View view = views.get(top);
        boolean created = view == null;
        if (view == null) {
            view = factory.apply(top);
            views.put(top, view);
            container.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        boolean push = previousCount < entries.size()
                && (previousTop == null || !previousTop.equals(top));
        boolean pop = previousTop != null && previousCount > entries.size();
        View previous = previousTop == null ? null : views.get(previousTop);
        if (push && previous != null && previous != view) {
            previous.setTranslationX(0f);
            previous.setVisibility(View.VISIBLE);
            previous.bringToFront();
        }
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
        int width = container.getWidth() > 0 ? container.getWidth()
                : container.getResources().getDisplayMetrics().widthPixels;
        if (!suppressAnimation && (push || pop) && width > 0) {
            if (push) {
                view.setTranslationX(width);
                view.animate().translationX(0f).setDuration(220).start();
                if (previous != null) {
                    previous.animate().translationX(-width * 0.22f).setDuration(220)
                            .withEndAction(() -> previous.setVisibility(View.GONE)).start();
                }
            } else {
                view.setTranslationX(-width * 0.22f);
                view.animate().translationX(0f).setDuration(200).start();
            }
        } else {
            view.setTranslationX(0f);
        }
        lastTop = top;
        lastCount = entries.size();
        if (view instanceof cn.lineai.ui.kit.Refreshable) {
            ((cn.lineai.ui.kit.Refreshable) view).onShown();
        }
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
        for (View view : views.values()) dispose(view);
        views.clear();
        navigation.close();
    }
}
