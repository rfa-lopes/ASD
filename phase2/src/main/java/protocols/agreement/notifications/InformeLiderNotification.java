package protocols.agreement.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.List;

public class InformeLiderNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 111;
    private Host lider;

    public InformeLiderNotification(Host lider) {
        super(NOTIFICATION_ID);
       this.lider = lider;
    }

    public Host getLider() {
        return lider;
    }

    public void setLider(Host lider) {
        this.lider = lider;
    }

    @Override
    public String toString() {
        return "JoinedNotification{" +
                "Host: " + lider +
                '}';
    }
}
