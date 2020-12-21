package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class HearBeatWaitTimer extends ProtoTimer {

    public static final short TIMER_ID = 102;

    public HearBeatWaitTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
