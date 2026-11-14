package cn.lineai.ipc.terminal;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Snapshot of one background shell task on the provider side, returned by a polled
 * {@code readShellBackground} call.
 *
 * <p>Instances are immutable value objects: the host polls with a byte offset and receives
 * the running flag, the exit code once finished, and the output recorded since that offset.
 */
public final class TerminalShellBackgroundState implements Parcelable {

    /** Provider-side handle identifying the background task. */
    public final long handleId;
    /** True while the process is still running. */
    public final boolean running;
    /** Exit code; valid only when {@code running} is false, -1 when unknown. */
    public final int exitCode;
    /** Output bytes recorded after {@code offset}; empty when nothing new. */
    public final String chunk;

    /**
     * Creates a snapshot; a null chunk is normalized to the empty string.
     *
     * @param handleId provider-side handle of the task
     * @param running whether the guest process is still alive
     * @param exitCode exit status once finished; -1 while unknown
     * @param chunk output recorded after the polled offset
     */
    public TerminalShellBackgroundState(long handleId, boolean running, int exitCode,
                                        String chunk) {
        this.handleId = handleId;
        this.running = running;
        this.exitCode = exitCode;
        this.chunk = chunk == null ? "" : chunk;
    }

    /** Rebuilds a snapshot from a parcel written by {@link #writeToParcel}. */
    private TerminalShellBackgroundState(Parcel in) {
        this(in.readLong(), in.readByte() != 0, in.readInt(), in.readString());
    }

    /** Writes the four fields in fixed order; field order is part of the wire format. */
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(handleId);
        dest.writeByte((byte) (running ? 1 : 0));
        dest.writeInt(exitCode);
        dest.writeString(chunk);
    }

    /** Returns 0: no special parcel contents. */
    @Override public int describeContents() {
        return 0;
    }

    /** Parcel creator matching the field order of {@link #writeToParcel}. */
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
