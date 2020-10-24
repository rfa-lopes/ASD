package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import java.io.IOException;

public class ShuffleReplyMessage extends ProtoMessage {

    public final static short MSG_ID = 9007;

    public ShuffleReplyMessage() {
        super(MSG_ID);
    }

    public static ISerializer<ShuffleReplyMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ShuffleReplyMessage shuffleReplyMessage, ByteBuf out) throws IOException {

        }

        @Override
        public ShuffleReplyMessage deserialize(ByteBuf in) throws IOException {
            return new ShuffleReplyMessage();
        }
    };
}
