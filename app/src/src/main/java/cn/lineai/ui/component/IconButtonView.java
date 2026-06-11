package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.widget.ImageButton;
import cn.lineai.R;

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

    private int iconType;
    private int iconColor = Color.WHITE;

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
        int padding = Math.max(0, Math.round((containerDp - iconDp) / 2f));
        setIconPaddingDp(padding, padding, padding, padding);
    }

    public int getIconType() {
        return iconType;
    }

    private void applyColor() {
        setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
    }

    private int drawableFor(int type) {
        switch (type) {
            case MENU:
                return R.drawable.ic_lucide_menu;
            case PLUS:
                return R.drawable.ic_lucide_plus;
            case SHIELD:
                return R.drawable.ic_lucide_shield;
            case MORE:
                return R.drawable.ic_lucide_ellipsis_vertical;
            case ARROW_UP:
                return R.drawable.ic_lucide_arrow_up;
            case STOP:
                return R.drawable.ic_lucide_square;
            case CHEVRON_DOWN:
                return R.drawable.ic_lucide_chevron_down;
            case CLOSE:
                return R.drawable.ic_lucide_x;
            case FOLDER_OPEN:
                return R.drawable.ic_lucide_folder_open;
            case FOLDER_PLUS:
                return R.drawable.ic_lucide_folder_plus;
            case ARCHIVE:
                return R.drawable.ic_lucide_archive;
            case MESSAGE_SQUARE:
                return R.drawable.ic_lucide_message_square;
            case TRASH_2:
                return R.drawable.ic_lucide_trash_2;
            case CHECK:
                return R.drawable.ic_lucide_check;
            case FILE_PLUS:
                return R.drawable.ic_lucide_file_plus;
            case COPY:
                return R.drawable.ic_lucide_copy;
            case ROTATE_CCW:
                return R.drawable.ic_lucide_rotate_ccw;
            case FOLDER:
                return R.drawable.ic_lucide_folder;
            case FILE:
                return R.drawable.ic_lucide_file;
            case FILE_TEXT:
                return R.drawable.ic_lucide_file_text;
            case FILE_CODE:
                return R.drawable.ic_lucide_file_code;
            case CHEVRON_LEFT:
                return R.drawable.ic_lucide_chevron_left;
            case CHEVRON_RIGHT:
                return R.drawable.ic_lucide_chevron_right;
            case BOX:
                return R.drawable.ic_lucide_box;
            case MONITOR:
                return R.drawable.ic_lucide_monitor;
            case BRAIN:
                return R.drawable.ic_lucide_brain;
            case PACKAGE:
                return R.drawable.ic_lucide_package;
            case PALETTE:
                return R.drawable.ic_lucide_palette;
            case DATABASE:
                return R.drawable.ic_lucide_database;
            case BOOK_OPEN:
                return R.drawable.ic_lucide_book_open;
            case BATTERY_CHARGING:
                return R.drawable.ic_lucide_battery_charging;
            case FLASK_CONICAL:
                return R.drawable.ic_lucide_flask_conical;
            case CPU:
                return R.drawable.ic_lucide_cpu;
            case ZAP:
                return R.drawable.ic_lucide_zap;
            case SMILE:
                return R.drawable.ic_lucide_smile;
            case EXPAND:
                return R.drawable.ic_lucide_expand;
            case SCROLL_TEXT:
                return R.drawable.ic_lucide_scroll_text;
            case SPARKLES:
                return R.drawable.ic_lucide_sparkles;
            case GLOBE:
                return R.drawable.ic_lucide_globe;
            case EXTERNAL_LINK:
                return R.drawable.ic_lucide_external_link;
            case MESSAGE_CIRCLE:
                return R.drawable.ic_lucide_message_circle;
            case SUN:
                return R.drawable.ic_lucide_sun;
            case MOON:
                return R.drawable.ic_lucide_moon;
            case COFFEE:
                return R.drawable.ic_lucide_coffee;
            case CODE:
                return R.drawable.ic_lucide_code;
            case CONTRAST:
                return R.drawable.ic_lucide_contrast;
            case GIT_BRANCH:
                return R.drawable.ic_lucide_git_branch;
            case PAINTBRUSH:
                return R.drawable.ic_lucide_paintbrush;
            case SAVE:
                return R.drawable.ic_lucide_save;
            case REFRESH_CW:
                return R.drawable.ic_lucide_refresh_cw;
            case UPLOAD:
                return R.drawable.ic_lucide_upload;
            case POWER:
                return R.drawable.ic_lucide_power;
            case SETTINGS:
                return R.drawable.ic_lucide_settings;
            case GIT_COMPARE:
                return R.drawable.ic_lucide_git_compare;
            case BELL:
                return R.drawable.ic_lucide_bell;
            case MUSIC:
                return R.drawable.ic_lucide_music;
            case SMARTPHONE:
                return R.drawable.ic_lucide_smartphone;
            case SQUARE_FUNCTION:
                return R.drawable.ic_lucide_square_function;
            case USER:
                return R.drawable.ic_lucide_user;
            case BUG:
                return R.drawable.ic_lucide_bug;
            case DOWNLOAD:
                return R.drawable.ic_lucide_download;
            case BOXES:
                return R.drawable.ic_lucide_boxes;
            case SLIDERS_HORIZONTAL:
                return R.drawable.ic_lucide_sliders_horizontal;
            case FILE_UP:
                return R.drawable.ic_lucide_file_up;
            case SEARCH:
                return R.drawable.ic_lucide_search;
            case SERVER:
                return R.drawable.ic_lucide_server;
            case TERMINAL:
                return R.drawable.ic_lucide_terminal;
            case SHIELD_CHECK:
                return R.drawable.ic_lucide_shield_check;
            case CLOCK_3:
                return R.drawable.ic_lucide_clock_3;
            case MESSAGE_SQUARE_TEXT:
                return R.drawable.ic_lucide_message_square_text;
            case BOT:
                return R.drawable.ic_lucide_bot;
            case CIRCLE_CHECK:
                return R.drawable.ic_lucide_circle_check;
            case CIRCLE_X:
                return R.drawable.ic_lucide_circle_x;
            case LOADER:
                return R.drawable.ic_lucide_loader;
            case FILE_PEN_LINE:
                return R.drawable.ic_lucide_file_pen_line;
            case WRENCH:
                return R.drawable.ic_lucide_wrench;
            case PLAY:
                return R.drawable.ic_lucide_play;
            case CIRCLE_ALERT:
                return R.drawable.ic_lucide_circle_alert;
            case MCP:
                return R.drawable.ic_lineai_mcp;
            default:
                return R.drawable.ic_lucide_plus;
        }
    }
}
