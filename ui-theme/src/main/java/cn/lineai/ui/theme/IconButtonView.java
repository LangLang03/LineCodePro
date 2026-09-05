package cn.lineai.ui.theme;
import cn.lineai.ui.theme.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.SparseArray;
import android.widget.ImageButton;

public final class IconButtonView extends ImageButton {
    public static final int MENU = 1;
    public static final int PLUS = 2;
    public static final int SHIELD = 3;
    public static final int MORE = 4;
    public static final int ARROW_UP = 5;
    public static final int STOP = 6;
    public static final int CHEVRON_DOWN = 7;
    public static final int CLOSE = 8;
    public static final int FOLDER_OPEN = 9;
    public static final int FOLDER_PLUS = 10;
    public static final int ARCHIVE = 11;
    public static final int MESSAGE_SQUARE = 12;
    public static final int TRASH_2 = 13;
    public static final int CHECK = 14;
    public static final int FILE_PLUS = 15;
    public static final int COPY = 16;
    public static final int ROTATE_CCW = 17;
    public static final int FOLDER = 18;
    public static final int FILE = 19;
    public static final int FILE_TEXT = 20;
    public static final int FILE_CODE = 21;
    public static final int CHEVRON_LEFT = 22;
    public static final int CHEVRON_RIGHT = 23;
    public static final int BOX = 24;
    public static final int MONITOR = 25;
    public static final int BRAIN = 26;
    public static final int PACKAGE = 27;
    public static final int PALETTE = 28;
    public static final int DATABASE = 29;
    public static final int BOOK_OPEN = 30;
    public static final int BATTERY_CHARGING = 31;
    public static final int FLASK_CONICAL = 32;
    public static final int CPU = 33;
    public static final int ZAP = 34;
    public static final int SMILE = 35;
    public static final int EXPAND = 36;
    public static final int SCROLL_TEXT = 37;
    public static final int SPARKLES = 38;
    public static final int GLOBE = 39;
    public static final int EXTERNAL_LINK = 40;
    public static final int MESSAGE_CIRCLE = 41;
    public static final int SUN = 42;
    public static final int MOON = 43;
    public static final int COFFEE = 44;
    public static final int CODE = 45;
    public static final int CONTRAST = 46;
    public static final int GIT_BRANCH = 47;
    public static final int PAINTBRUSH = 48;
    public static final int SAVE = 49;
    public static final int REFRESH_CW = 50;
    public static final int UPLOAD = 51;
    public static final int POWER = 52;
    public static final int SETTINGS = 53;
    public static final int GIT_COMPARE = 54;
    public static final int BELL = 55;
    public static final int MUSIC = 56;
    public static final int SMARTPHONE = 57;
    public static final int SQUARE_FUNCTION = 58;
    public static final int USER = 59;
    public static final int BUG = 60;
    public static final int DOWNLOAD = 61;
    public static final int BOXES = 62;
    public static final int SLIDERS_HORIZONTAL = 63;
    public static final int FILE_UP = 64;
    public static final int SEARCH = 65;
    public static final int SERVER = 66;
    public static final int TERMINAL = 67;
    public static final int SHIELD_CHECK = 68;
    public static final int CLOCK_3 = 69;
    public static final int MESSAGE_SQUARE_TEXT = 70;
    public static final int BOT = 71;
    public static final int CIRCLE_CHECK = 72;
    public static final int CIRCLE_X = 73;
    public static final int LOADER = 74;
    public static final int FILE_PEN_LINE = 75;
    public static final int WRENCH = 76;
    public static final int PLAY = 77;
    public static final int CIRCLE_ALERT = 78;
    public static final int MCP = 79;
    public static final int SHARE = 80;
    public static final int QUOTE = 81;
    public static final int TEXT_CURSOR = 82;
    public static final int CHECK_SQUARE = 83;
    public static final int IMAGE = 84;

    private static final SparseArray<Integer> ICON_MAP = new SparseArray<>();
    static {
        ICON_MAP.put(MENU, R.drawable.ic_lucide_menu);
        ICON_MAP.put(PLUS, R.drawable.ic_lucide_plus);
        ICON_MAP.put(SHIELD, R.drawable.ic_lucide_shield);
        ICON_MAP.put(MORE, R.drawable.ic_lucide_ellipsis_vertical);
        ICON_MAP.put(ARROW_UP, R.drawable.ic_lucide_arrow_up);
        ICON_MAP.put(STOP, R.drawable.ic_lucide_square);
        ICON_MAP.put(CHEVRON_DOWN, R.drawable.ic_lucide_chevron_down);
        ICON_MAP.put(CLOSE, R.drawable.ic_lucide_x);
        ICON_MAP.put(FOLDER_OPEN, R.drawable.ic_lucide_folder_open);
        ICON_MAP.put(FOLDER_PLUS, R.drawable.ic_lucide_folder_plus);
        ICON_MAP.put(ARCHIVE, R.drawable.ic_lucide_archive);
        ICON_MAP.put(MESSAGE_SQUARE, R.drawable.ic_lucide_message_square);
        ICON_MAP.put(TRASH_2, R.drawable.ic_lucide_trash_2);
        ICON_MAP.put(CHECK, R.drawable.ic_lucide_check);
        ICON_MAP.put(FILE_PLUS, R.drawable.ic_lucide_file_plus);
        ICON_MAP.put(COPY, R.drawable.ic_lucide_copy);
        ICON_MAP.put(ROTATE_CCW, R.drawable.ic_lucide_rotate_ccw);
        ICON_MAP.put(FOLDER, R.drawable.ic_lucide_folder);
        ICON_MAP.put(FILE, R.drawable.ic_lucide_file);
        ICON_MAP.put(FILE_TEXT, R.drawable.ic_lucide_file_text);
        ICON_MAP.put(FILE_CODE, R.drawable.ic_lucide_file_code);
        ICON_MAP.put(CHEVRON_LEFT, R.drawable.ic_lucide_chevron_left);
        ICON_MAP.put(CHEVRON_RIGHT, R.drawable.ic_lucide_chevron_right);
        ICON_MAP.put(BOX, R.drawable.ic_lucide_box);
        ICON_MAP.put(MONITOR, R.drawable.ic_lucide_monitor);
        ICON_MAP.put(BRAIN, R.drawable.ic_lucide_brain);
        ICON_MAP.put(PACKAGE, R.drawable.ic_lucide_package);
        ICON_MAP.put(PALETTE, R.drawable.ic_lucide_palette);
        ICON_MAP.put(DATABASE, R.drawable.ic_lucide_database);
        ICON_MAP.put(BOOK_OPEN, R.drawable.ic_lucide_book_open);
        ICON_MAP.put(BATTERY_CHARGING, R.drawable.ic_lucide_battery_charging);
        ICON_MAP.put(FLASK_CONICAL, R.drawable.ic_lucide_flask_conical);
        ICON_MAP.put(CPU, R.drawable.ic_lucide_cpu);
        ICON_MAP.put(ZAP, R.drawable.ic_lucide_zap);
        ICON_MAP.put(SMILE, R.drawable.ic_lucide_smile);
        ICON_MAP.put(EXPAND, R.drawable.ic_lucide_expand);
        ICON_MAP.put(SCROLL_TEXT, R.drawable.ic_lucide_scroll_text);
        ICON_MAP.put(SPARKLES, R.drawable.ic_lucide_sparkles);
        ICON_MAP.put(GLOBE, R.drawable.ic_lucide_globe);
        ICON_MAP.put(EXTERNAL_LINK, R.drawable.ic_lucide_external_link);
        ICON_MAP.put(MESSAGE_CIRCLE, R.drawable.ic_lucide_message_circle);
        ICON_MAP.put(SUN, R.drawable.ic_lucide_sun);
        ICON_MAP.put(MOON, R.drawable.ic_lucide_moon);
        ICON_MAP.put(COFFEE, R.drawable.ic_lucide_coffee);
        ICON_MAP.put(CODE, R.drawable.ic_lucide_code);
        ICON_MAP.put(CONTRAST, R.drawable.ic_lucide_contrast);
        ICON_MAP.put(GIT_BRANCH, R.drawable.ic_lucide_git_branch);
        ICON_MAP.put(PAINTBRUSH, R.drawable.ic_lucide_paintbrush);
        ICON_MAP.put(SAVE, R.drawable.ic_lucide_save);
        ICON_MAP.put(REFRESH_CW, R.drawable.ic_lucide_refresh_cw);
        ICON_MAP.put(UPLOAD, R.drawable.ic_lucide_upload);
        ICON_MAP.put(POWER, R.drawable.ic_lucide_power);
        ICON_MAP.put(SETTINGS, R.drawable.ic_lucide_settings);
        ICON_MAP.put(GIT_COMPARE, R.drawable.ic_lucide_git_compare);
        ICON_MAP.put(BELL, R.drawable.ic_lucide_bell);
        ICON_MAP.put(MUSIC, R.drawable.ic_lucide_music);
        ICON_MAP.put(SMARTPHONE, R.drawable.ic_lucide_smartphone);
        ICON_MAP.put(SQUARE_FUNCTION, R.drawable.ic_lucide_square_function);
        ICON_MAP.put(USER, R.drawable.ic_lucide_user);
        ICON_MAP.put(BUG, R.drawable.ic_lucide_bug);
        ICON_MAP.put(DOWNLOAD, R.drawable.ic_lucide_download);
        ICON_MAP.put(BOXES, R.drawable.ic_lucide_boxes);
        ICON_MAP.put(SLIDERS_HORIZONTAL, R.drawable.ic_lucide_sliders_horizontal);
        ICON_MAP.put(FILE_UP, R.drawable.ic_lucide_file_up);
        ICON_MAP.put(SEARCH, R.drawable.ic_lucide_search);
        ICON_MAP.put(SERVER, R.drawable.ic_lucide_server);
        ICON_MAP.put(TERMINAL, R.drawable.ic_lucide_terminal);
        ICON_MAP.put(SHIELD_CHECK, R.drawable.ic_lucide_shield_check);
        ICON_MAP.put(CLOCK_3, R.drawable.ic_lucide_clock_3);
        ICON_MAP.put(MESSAGE_SQUARE_TEXT, R.drawable.ic_lucide_message_square_text);
        ICON_MAP.put(BOT, R.drawable.ic_lucide_bot);
        ICON_MAP.put(CIRCLE_CHECK, R.drawable.ic_lucide_circle_check);
        ICON_MAP.put(CIRCLE_X, R.drawable.ic_lucide_circle_x);
        ICON_MAP.put(LOADER, R.drawable.ic_lucide_loader);
        ICON_MAP.put(FILE_PEN_LINE, R.drawable.ic_lucide_file_pen_line);
        ICON_MAP.put(WRENCH, R.drawable.ic_lucide_wrench);
        ICON_MAP.put(PLAY, R.drawable.ic_lucide_play);
        ICON_MAP.put(CIRCLE_ALERT, R.drawable.ic_lucide_circle_alert);
        ICON_MAP.put(MCP, R.drawable.ic_lineai_mcp);
        ICON_MAP.put(SHARE, R.drawable.ic_lucide_share_2);
        ICON_MAP.put(QUOTE, R.drawable.ic_lucide_quote);
        ICON_MAP.put(TEXT_CURSOR, R.drawable.ic_lucide_text_cursor);
        ICON_MAP.put(CHECK_SQUARE, R.drawable.ic_lucide_check_square);
        ICON_MAP.put(IMAGE, R.drawable.ic_lucide_image);
    }

    private int iconType;
    private int iconColor = Color.WHITE;

    @Override public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        setFocusable(clickable);
        setImportantForAccessibility(clickable ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public IconButtonView(Context context, int iconType) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
        setAdjustViewBounds(false);
        setPadding(0, 0, 0, 0);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        setFocusable(true);
        setIconType(iconType);
    }

    public void setIconType(int iconType) {
        this.iconType = iconType;
        setImageResource(drawableFor(iconType));
        applyColor();
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
        applyColor();
    }

    public void setIconPaddingDp(int left, int top, int right, int bottom) {
        float density = getResources().getDisplayMetrics().density;
        setPadding(
                Math.round(left * density),
                Math.round(top * density),
                Math.round(right * density),
                Math.round(bottom * density)
        );
    }

    public void setIconSizeDp(int containerDp, int iconDp) {
        requestedIconDp = iconDp;
        int padding = Math.max(0, Math.round((containerDp - iconDp) / 2f));
        setIconPaddingDp(padding, padding, padding, padding);
        updateIconPadding(getWidth(), getHeight());
    }

    private int requestedIconDp;

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateIconPadding(width, height);
    }

    private void updateIconPadding(int width, int height) {
        if (requestedIconDp <= 0 || width <= 0 || height <= 0) return;
        int size = Math.min(Math.min(width, height), LineTheme.dp(getContext(), requestedIconDp));
        int horizontal = (width - size) / 2;
        int vertical = (height - size) / 2;
        setPadding(horizontal, vertical, width - size - horizontal, height - size - vertical);
    }

    public int getIconType() {
        return iconType;
    }

    private void applyColor() {
        setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
    }

    private int drawableFor(int type) {
        Integer resId = ICON_MAP.get(type);
        return resId != null ? resId : R.drawable.ic_lucide_plus;
    }
}
