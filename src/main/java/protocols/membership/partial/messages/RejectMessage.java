package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;

import java.io.IOException;

public class RejectMessage extends ProtoMessage {
    public final static short MSG_ID = 9005;

    public RejectMessage() {
        super(MSG_ID);
    }

    @Override
    public String toString() {
        return "RejectMessage{}";
    }

    public static final ISerializer<RejectMessage> serializer = new ISerializer<>()  {
        @Override
        public void serialize(RejectMessage rejectMessage, ByteBuf out) throws IOException{
        }

        @Override
        public RejectMessage deserialize(ByteBuf in) throws IOException {
            return new RejectMessage();
        }

    };
}
