package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class JoinReplyMessage extends ProtoMessage {

    public final static short MSG_ID = 9004;

    public JoinReplyMessage() {
        super(MSG_ID);
    }

    @Override
    public String toString() {
        return "JoinReplyMessage{}";
    }

    public static ISerializer<JoinReplyMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(JoinReplyMessage joinMessage, ByteBuf out) throws IOException { }

        @Override
        public JoinReplyMessage deserialize(ByteBuf in) throws IOException {
            return new JoinReplyMessage();
        }
    };
}
