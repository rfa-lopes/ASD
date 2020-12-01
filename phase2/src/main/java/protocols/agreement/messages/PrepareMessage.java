package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class PrepareMessage extends ProtoMessage {

    public final static short MSG_CODE = 9022;

    private final UUID opId;

    public PrepareMessage(UUID opId) {
        super(MSG_CODE);
        this.opId = opId;
    }

    public UUID getOpId() {
        return opId;
    }

    @Override
    public String toString() {
        return "PrepareMessage{" +
                "opId: " + opId +
                "}";
    }

    public static final ISerializer<PrepareMessage> serializer = new ISerializer<PrepareMessage>()  {
        @Override
        public void serialize(PrepareMessage prepareMessage, ByteBuf out) throws IOException {
            out.writeLong(prepareMessage.getOpId().getMostSignificantBits());
            out.writeLong(prepareMessage.getOpId().getLeastSignificantBits());
        }

        @Override
        public PrepareMessage deserialize(ByteBuf in) throws IOException {
            long high = in.readLong();
            long low = in.readLong();
            return new PrepareMessage(new UUID(high, low));
        }
    };
}
