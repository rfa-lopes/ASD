package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;

/*Made by Rodrigo*/
public class AcceptOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9025;

    private final int sequenceNumber;

    public AcceptOkMessage(int sequenceNumber) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "AcceptOkMessage{" +
                "sequenceNumber: " + sequenceNumber +
                "}";
    }

    public static final ISerializer<AcceptOkMessage> serializer = new ISerializer<AcceptOkMessage>()  {
        @Override
        public void serialize(AcceptOkMessage acceptMessage, ByteBuf out) throws IOException {
            out.writeInt(acceptMessage.getSequenceNumber());
        }

        @Override
        public AcceptOkMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            return new AcceptOkMessage(sequenceNumber);
        }
    };
}
