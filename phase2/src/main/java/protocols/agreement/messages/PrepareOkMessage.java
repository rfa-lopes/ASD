package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class PrepareOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9023;

    private final int sequenceNumber;

    public PrepareOkMessage(int sequenceNumber) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "PrepareOkMessage{" +
                "sequenceNumber: " + sequenceNumber +
                "}";
    }

    public static final ISerializer<PrepareOkMessage> serializer = new ISerializer<PrepareOkMessage>()  {
        @Override
        public void serialize(PrepareOkMessage prepareOkMessage, ByteBuf out) throws IOException {
            out.writeInt(prepareOkMessage.getSequenceNumber());
            out.writeInt(prepareOkMessage.getSequenceNumber());
        }

        @Override
        public PrepareOkMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            return new PrepareOkMessage(sequenceNumber);
        }
    };
}
