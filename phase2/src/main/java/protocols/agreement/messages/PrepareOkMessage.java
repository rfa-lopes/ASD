package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class PrepareOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9023;

    private final UUID opId;

    public PrepareOkMessage(UUID opId) {
        super(MSG_CODE);
        this.opId = opId;
    }

    public UUID getOpId() {
        return opId;
    }

    @Override
    public String toString() {
        return "PrepareOkMessage{" +
                "opId: " + opId +
                "}";
    }

    public static final ISerializer<PrepareOkMessage> serializer = new ISerializer<PrepareOkMessage>()  {
        @Override
        public void serialize(PrepareOkMessage prepareOkMessage, ByteBuf out) throws IOException {
            out.writeLong(prepareOkMessage.getOpId().getMostSignificantBits());
            out.writeLong(prepareOkMessage.getOpId().getLeastSignificantBits());
        }

        @Override
        public PrepareOkMessage deserialize(ByteBuf in) throws IOException {
            long high = in.readLong();
            long low = in.readLong();
            return new PrepareOkMessage(new UUID(high, low));
        }
    };
}
