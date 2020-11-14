package protocols.membership.partial.timers;

import babel.generic.ProtoTimer;

public class JoinTimer extends ProtoTimer {

    public static final short TIMER_ID = 9010;

    public JoinTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return null;
    }
}
