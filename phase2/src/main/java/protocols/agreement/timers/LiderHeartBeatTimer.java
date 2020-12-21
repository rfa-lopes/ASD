package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class LiderHeartBeatTimer extends ProtoTimer {
    public static final short TIMER_ID = 103;

    public LiderHeartBeatTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
