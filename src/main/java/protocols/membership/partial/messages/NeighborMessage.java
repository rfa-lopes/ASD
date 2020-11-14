package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import java.io.IOException;

public class NeighborMessage extends ProtoMessage {

    public final static short MSG_ID = 9005;
    private final boolean isPriority;

    public NeighborMessage(boolean isPriority) {
        super(MSG_ID);
        this.isPriority = isPriority;
    }

    public boolean isPriority() {
        return isPriority;
    }

    @Override
    public String toString() {
        return "NeighborMessage{" +
                "isPriority=" + isPriority +
                '}';
    }

    public static ISerializer<NeighborMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(NeighborMessage neighborMessage, ByteBuf out) throws IOException {
            out.writeBoolean(neighborMessage.isPriority());
        }

        @Override
        public NeighborMessage deserialize(ByteBuf in) throws IOException {
            boolean isPriority = in.readBoolean();
            return new NeighborMessage(isPriority);
        }
    };
}
