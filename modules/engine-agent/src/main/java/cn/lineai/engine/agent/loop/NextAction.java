package cn.lineai.engine.agent.loop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Decider output: the next state plus effects to execute outside the reducer. */
public final class NextAction {

    public interface Effect { }

    public final ToolLoopState state;
    public final List<Effect> effects;

    private NextAction(ToolLoopState state, List<Effect> effects) {
        this.state = state;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }

    public static NextAction of(ToolLoopState state, Effect... effects) {
        List<Effect> list = new ArrayList<>();
        Collections.addAll(list, effects);
        return new NextAction(state, list);
    }

    public boolean has(Class<? extends Effect> type) {
        for (Effect effect : effects) {
            if (type.isInstance(effect)) return true;
        }
        return false;
    }

    public static final class CallModel implements Effect { }

    public static final class ExecuteTools implements Effect { }

    public static final class RequestConfirmation implements Effect { }

    public static final class NotifyBudgetExceeded implements Effect {
        public final int requested;
        public final int used;
        public final int max;

        public NotifyBudgetExceeded(int requested, int used, int max) {
            this.requested = requested;
            this.used = used;
            this.max = max;
        }
    }

    public static final class NotifyFinished implements Effect { }

    public static final class NotifyFailed implements Effect { }
}
