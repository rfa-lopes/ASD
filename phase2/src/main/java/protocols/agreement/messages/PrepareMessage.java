package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class PrepareMessage extends ProtoMessage {

    public final static short MSG_CODE = 107;

    private final int sequenceNumber;

    public PrepareMessage(int sequenceNumber) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "PrepareMessage{" +
                "sequenceNumber: " + sequenceNumber +
                "}";
    }

    public static final ISerializer<PrepareMessage> serializer = new ISerializer<PrepareMessage>()  {
        @Override
        public void serialize(PrepareMessage prepareMessage, ByteBuf out) throws IOException {
            out.writeInt(prepareMessage.getSequenceNumber());
        }

        @Override
        public PrepareMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            return new PrepareMessage(sequenceNumber);
        }
    };
}
