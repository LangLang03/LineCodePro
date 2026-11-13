package cn.lineai.ipc.terminal;

import android.os.Parcel;
import android.os.Parcelable;

/** Snapshot of one background shell task on the provider side. */
public final class TerminalShellBackgroundState implements Parcelable {

    public final long handleId;
    /** True while the process is still running. */
    public final boolean running;
    /** Exit code; valid only when {@code running} is false, -1 when unknown. */
    public final int exitCode;
    /** Output bytes recorded after {@code offset}; empty when nothing new. */
    public final String chunk;

    public TerminalShellBackgroundState(long handleId, boolean running, int exitCode,
                                        String chunk) {
        this.handleId = handleId;
        this.running = running;
        this.exitCode = exitCode;
        this.chunk = chunk == null ? "" : chunk;
    }

    private TerminalShellBackgroundState(Parcel in) {
        this(in.readLong(), in.readByte() != 0, in.readInt(), in.readString());
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(handleId);
        dest.writeByte((byte) (running ? 1 : 0));
        dest.writeInt(exitCode);
        dest.writeString(chunk);
    }

    @Override public int describeContents() {
        return 0;
    }

    public static final Creator<TerminalShellBackgroundState> CREATOR =
            new Creator<TerminalShellBackgroundState>() {
                @Override public TerminalShellBackgroundState createFromParcel(Parcel in) {
                    return new TerminalShellBackgroundState(in);
                }

                @Override public TerminalShellBackgroundState[] newArray(int size) {
                    return new TerminalShellBackgroundState[size];
                }
            };
}
