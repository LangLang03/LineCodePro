package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Optional dedicated compression model for protocols that support it. */
final class CompressionSection extends LinearLayout {

    interface Catalog {
        List<String> fetch(ModelConfig.Protocol protocol, String baseUrl, String apiKey) throws Exception;
    }

    interface Destination {
        void onStateChanged();
    }

    interface Picker {
        void pick(List<String> ids, String selected, ModelPickerSheet.Listener listener);
    }

    private final Tokens tokens;
    private final ModelConfig.Protocol[] protocol;
    private final Supplier<String> baseUrl;
    private final Supplier<String> apiKey;
    private final Destination destination;
    private final Catalog catalog;
    private final Picker picker;
    private final Scheduler scheduler;
    private final Switch enabledSwitch;
    private final Switch autoSwitch;
    private final Switch customSwitch;
    private final EditText modelInput;
    private final LinearLayout detail;
    private final LinearLayout inputHost;
    private final TextView pickLabel;
    private final LinearLayout queryButton;
    private final TextView queryText;
    private final IconButton queryIcon;
    private final ArrayList<String> fetched = new ArrayList<>();
    private String selectedId;
    private boolean fetching;

    CompressionSection(Context context, Tokens tokens, ModelConfig.Protocol[] protocol,
                       boolean enabled, boolean auto, String modelId, Catalog catalog,
                       Picker picker, Scheduler scheduler, Supplier<String> baseUrl,
                       Supplier<String> apiKey, Destination destination) {
        super(context);
        this.tokens = tokens;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.catalog = catalog;
        this.picker = picker;
        this.scheduler = scheduler;
        this.destination = destination;
        setOrientation(VERTICAL);
        selectedId = modelId == null ? "" : modelId;

        LinearLayout header = row();
        header.addView(FormFields.label(context, tokens,
                context.getString(R.string.screen_model_add_compaction_label)),
                new LayoutParams(0, -2, 1f));
        enabledSwitch = toggle(enabled);
        header.addView(enabledSwitch, new LayoutParams(-2, -2));
        addView(header, FormFields.labelParams(context, 16, 8));

        detail = new LinearLayout(context);
        detail.setOrientation(VERTICAL);
        addView(detail, new LayoutParams(-1, -2));
        TextView hint = Ui.text(context, context.getString(R.string.screen_model_add_compaction_hint),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        hint.setLineSpacing(Ui.dp(context, 3), 1f);
        detail.addView(hint, new LayoutParams(-1, -2));

        LinearLayout autoRow = row();
        autoRow.addView(FormFields.label(context, tokens,
                context.getString(R.string.screen_model_add_compaction_auto_label)),
                new LayoutParams(0, -2, 1f));
        autoSwitch = toggle(auto);
        autoRow.addView(autoSwitch, new LayoutParams(-2, -2));
        detail.addView(autoRow, FormFields.labelParams(context, 12, 8));

        LinearLayout idRow = row();
        idRow.addView(FormFields.label(context, tokens,
                context.getString(R.string.screen_model_add_compaction_id_label)),
                new LayoutParams(0, -2, 1f));
        LinearLayout switchWrap = row();
        switchWrap.addView(Ui.medium(context, context.getString(R.string.screen_model_add_custom_id_label),
                Tokens.FONT_SM, tokens.textSecondary), new LayoutParams(-2, -2));
        customSwitch = toggle(modelId != null && !modelId.isEmpty());
        LayoutParams customParams = new LayoutParams(-2, -2);
        customParams.leftMargin = Ui.dp(context, 8);
        switchWrap.addView(customSwitch, customParams);
        idRow.addView(switchWrap, new LayoutParams(-2, -2));
        detail.addView(idRow, FormFields.labelParams(context, 12, 8));

        inputHost = new LinearLayout(context);
        inputHost.setOrientation(VERTICAL);
        detail.addView(inputHost, new LayoutParams(-1, -2));
        modelInput = FormFields.input(context, tokens, selectedId,
                context.getString(R.string.screen_model_add_compaction_id_hint), false, false);
        pickLabel = Ui.text(context, context.getString(R.string.screen_model_add_pick_first),
                Tokens.FONT_MD, tokens.textTertiary, Typeface.NORMAL);
        pickLabel.setSingleLine(true);
        pickLabel.setEllipsize(TextUtils.TruncateAt.END);
        queryButton = queryButton(context);
        queryText = Ui.bold(context, context.getString(R.string.screen_model_add_query_button),
                Tokens.FONT_MD, tokens.textTertiary);
        LayoutParams queryTextParams = new LayoutParams(-2, -2);
        queryTextParams.leftMargin = Ui.dp(context, 4);
        queryButton.addView(queryText, queryTextParams);
        queryIcon = (IconButton) queryButton.getChildAt(0);

        enabledSwitch.setOnCheckedChangeListener((view, checked) -> {
            updateVisibility();
            notifyChanged();
        });
        autoSwitch.setOnCheckedChangeListener((view, checked) -> {
            renderInput(customSwitch.isChecked());
            notifyChanged();
        });
        customSwitch.setOnCheckedChangeListener((view, checked) -> {
            renderInput(checked);
            notifyChanged();
        });
        queryButton.setOnClickListener(v -> query());
        updateVisibility();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Switch toggle(boolean checked) {
        Switch toggle = new Switch(getContext());
        FormFields.tintSwitch(toggle, tokens);
        toggle.setChecked(checked);
        return toggle;
    }

    private LinearLayout queryButton(Context context) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(Ui.dp(context, 76));
        Ui.padding(button, 16, 0, 16, 0);
        IconButton icon = new IconButton(context, Icons.SEARCH);
        icon.setClickable(false);
        icon.setIconSizeDp(16, 16);
        button.addView(icon, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        return button;
    }

    boolean compressionEnabled() {
        return enabledSwitch.isChecked() && protocol[0].supportsDedicatedCompression();
    }

    boolean isAuto() {
        return autoSwitch.isChecked();
    }

    String modelId() {
        return customSwitch.isChecked() ? FormFields.value(modelInput) : selectedId;
    }

    boolean isReady() {
        return !compressionEnabled() || isAuto() || !modelId().isEmpty();
    }

    void clearFetched() {
        fetched.clear();
        selectedId = "";
        if (!customSwitch.isChecked()) renderInput(false);
    }

    void updateForProtocolChange() {
        updateVisibility();
    }

    private void updateVisibility() {
        boolean supported = protocol[0].supportsDedicatedCompression();
        setVisibility(supported ? VISIBLE : GONE);
        if (!supported && enabledSwitch.isChecked()) enabledSwitch.setChecked(false);
        boolean enabled = supported && enabledSwitch.isChecked();
        detail.setVisibility(enabled ? VISIBLE : GONE);
        autoSwitch.setEnabled(enabled);
        autoSwitch.setAlpha(enabled ? 1f : 0.45f);
        boolean customEnabled = enabled && !autoSwitch.isChecked();
        customSwitch.setEnabled(customEnabled);
        customSwitch.setAlpha(customEnabled ? 1f : 0.45f);
        renderInput(customSwitch.isChecked());
        updateQueryState();
    }

    private void renderInput(boolean custom) {
        inputHost.removeAllViews();
        if (!compressionEnabled() || isAuto()) {
            inputHost.setVisibility(GONE);
            return;
        }
        inputHost.setVisibility(VISIBLE);
        if (custom) {
            FormFields.detach(modelInput);
            inputHost.addView(modelInput, new LayoutParams(-1, -2));
            return;
        }
        Context context = getContext();
        LinearLayout row = row();
        LinearLayout selector = new LinearLayout(context);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 12, tokens.borderLight));
        Ui.padding(selector, 16, 12, 16, 12);
        boolean has = !selectedId.isEmpty();
        pickLabel.setText(has ? selectedId : context.getString(R.string.screen_model_add_pick_first));
        pickLabel.setTextColor(has ? tokens.text : tokens.textTertiary);
        selector.addView(pickLabel, new LayoutParams(0, -2, 1f));
        IconButton down = new IconButton(context, Icons.CHEVRON_DOWN);
        down.setIconColor(tokens.textTertiary);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        selector.addView(down, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        selector.setOnClickListener(v -> queryButton.performClick());
        row.addView(selector, new LayoutParams(0, Ui.dp(context, 48), 1f));
        LayoutParams queryParams = new LayoutParams(-2, Ui.dp(context, 48));
        queryParams.leftMargin = Ui.dp(context, 8);
        row.addView(queryButton, queryParams);
        inputHost.addView(row, new LayoutParams(-1, -2));
    }

    private boolean canQuery() {
        return compressionEnabled() && !isAuto() && !baseUrl.get().isEmpty() && !apiKey.get().isEmpty();
    }

    private void updateQueryState() {
        boolean can = canQuery() && !fetching;
        queryText.setText(fetching ? R.string.screen_model_add_query_button_loading
                : R.string.screen_model_add_query_button);
        queryText.setTextColor(can || fetching ? tokens.textOnColor : tokens.textTertiary);
        queryIcon.setIconColor(can || fetching ? tokens.textOnColor : tokens.textTertiary);
        queryButton.setBackground(Ui.rounded(getContext(), can || fetching
                ? tokens.accent : tokens.surfaceLight, 12));
        queryButton.setEnabled(can);
    }

    private void query() {
        if (!canQuery() || fetching) return;
        if (!fetched.isEmpty()) {
            picker.pick(fetched, selectedId, this::selected);
            return;
        }
        fetching = true;
        updateQueryState();
        final ModelConfig.Protocol type = protocol[0];
        final String url = baseUrl.get();
        final String key = apiKey.get();
        scheduler.io(() -> {
            try {
                List<String> ids = catalog.fetch(type, url, key);
                scheduler.main(() -> {
                    fetching = false;
                    fetched.clear();
                    if (ids != null) fetched.addAll(ids);
                    updateQueryState();
                    if (fetched.isEmpty()) {
                        Toast.makeText(getContext(), R.string.screen_model_add_fetch_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    picker.pick(fetched, selectedId, this::selected);
                });
            } catch (Exception error) {
                scheduler.main(() -> {
                    fetching = false;
                    updateQueryState();
                    Toast.makeText(getContext(), error.getMessage() == null
                            ? getContext().getString(R.string.toast_query_failed)
                            : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void selected(String id, boolean custom) {
        if (custom) {
            selectedId = "";
            customSwitch.setChecked(true);
            modelInput.setText("");
            renderInput(true);
        } else {
            selectedId = id;
            customSwitch.setChecked(false);
            renderInput(false);
        }
        notifyChanged();
    }

    private void notifyChanged() {
        if (destination != null) destination.onStateChanged();
    }
}
