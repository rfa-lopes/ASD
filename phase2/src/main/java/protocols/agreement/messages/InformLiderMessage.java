package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class InformLiderMessage extends ProtoMessage {

    public final static short MSG_CODE = 9026;

    private final Host host;



    public InformLiderMessage(Host host) {
        super(MSG_CODE);
        this.host = host;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "InformLider{" +
                "Host: " + host +
                "}";
    }

}
