package protocols.membership.partial.timers;

import babel.generic.ProtoTimer;

public class ShuffleTimer extends ProtoTimer {

    public static final short TIMER_ID = 9007;

    public ShuffleTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return null;
    }
}
