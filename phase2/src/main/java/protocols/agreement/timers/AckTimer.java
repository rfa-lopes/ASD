package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class AckTimer extends ProtoTimer {
    public static final short TIMER_ID = 9026;

    public AckTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
