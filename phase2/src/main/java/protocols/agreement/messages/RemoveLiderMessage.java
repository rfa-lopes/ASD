package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RemoveLiderMessage extends ProtoMessage {

    public final static short MSG_CODE = 9126;

    private final Host host;



    public RemoveLiderMessage(Host host) {
        super(MSG_CODE);
        this.host = host;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "Remove Lider{" +
                "Host: " + host +
                "}";
    }

    public static final ISerializer<RemoveLiderMessage> serializer = new ISerializer<RemoveLiderMessage>() {
        @Override
        public void serialize(RemoveLiderMessage msg, ByteBuf out) {
            InetAddress tosend = msg.getHost().getAddress();
            out.writeBytes(tosend.getAddress());
            out.writeShort(msg.getHost().getPort());
        }

        @Override
        public RemoveLiderMessage deserialize(ByteBuf in) throws UnknownHostException {
            byte[] addrBytes = new byte[4];
            in.readBytes(addrBytes);
            int port = in.readShort() & '\uffff';
            InetAddress received = InetAddress.getByAddress(addrBytes);
            return new RemoveLiderMessage(new Host(received, port));
        }
    };



}
