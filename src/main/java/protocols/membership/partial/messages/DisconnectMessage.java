package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class DisconnectMessage extends ProtoMessage {

    public final static short MSG_ID = 9001;

    public DisconnectMessage() {
        super(MSG_ID);
    }

    @Override
    public String toString() {
        return "DisconnectMessage{}";
    }

    public static ISerializer<DisconnectMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(DisconnectMessage disconnectMessage, ByteBuf out) throws IOException {
        }

        @Override
        public DisconnectMessage deserialize(ByteBuf in) throws IOException {
            return new DisconnectMessage();
        }
    };
}
