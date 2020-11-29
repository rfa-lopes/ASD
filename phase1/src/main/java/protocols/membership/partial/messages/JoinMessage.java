package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import java.io.IOException;

public class JoinMessage extends ProtoMessage {

    public final static short MSG_ID = 9003;

    public JoinMessage() {
        super(MSG_ID);
    }

    @Override
    public String toString() {
        return "JoinMessage{}";
    }

    public static ISerializer<JoinMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(JoinMessage joinMessage, ByteBuf out) throws IOException {
        }

        @Override
        public JoinMessage deserialize(ByteBuf in) throws IOException {
            return new JoinMessage();
        }
    };
}
