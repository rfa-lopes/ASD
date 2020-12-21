package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class AcceptOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 103;

    private final int sequenceNumber;
    private final UUID opId;
    private final byte[] operation;

    public AcceptOkMessage(int sequenceNumber, UUID opId, byte[] operation) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
        this.opId = opId;
        this.operation = operation;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public UUID getOpId() {
        return opId;
    }

    public byte[] getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "AcceptOkMessage{" +
                "sequenceNumber= " + sequenceNumber +
                ", opId= " + opId +
                ", operation=" + Hex.encodeHexString(operation) +
                "}";
    }

    public static final ISerializer<AcceptOkMessage> serializer = new ISerializer<AcceptOkMessage>()  {
        @Override
        public void serialize(AcceptOkMessage acceptOkMessage, ByteBuf out) throws IOException {
            out.writeInt(acceptOkMessage.getSequenceNumber());
            if(acceptOkMessage.getOpId() != null && acceptOkMessage.getOperation() != null) {
                out.writeLong(acceptOkMessage.getOpId().getLeastSignificantBits());
                out.writeLong(acceptOkMessage.getOpId().getMostSignificantBits());
                out.writeInt(acceptOkMessage.getOperation().length);
                out.writeBytes(acceptOkMessage.getOperation());
            }
        }

        @Override
        public AcceptOkMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            UUID opId = new UUID(in.readLong(), in.readLong());
            int bytesLength = in.readInt();

            byte[] operation = new byte[bytesLength];
            for(int i = 0; i < bytesLength; i++)
                operation[i] = in.readByte();

            return new AcceptOkMessage(sequenceNumber, opId, operation);
        }
    };
}
