package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class AcceptMessage extends ProtoMessage {

    public final static short MSG_CODE = 9024;

    private final int sequenceNumber;
    private final UUID opId;
    private final byte[] operation;

    public AcceptMessage(int sequenceNumber, UUID opId, byte[] operation) {
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
        return "AcceptMessage{" +
                "sequenceNumber= " + sequenceNumber +
                ", opId= " + opId +
                ", operation=" + Hex.encodeHexString(operation) +
                "}";
    }

    public static final ISerializer<AcceptMessage> serializer = new ISerializer<AcceptMessage>()  {
        @Override
        public void serialize(AcceptMessage acceptMessage, ByteBuf out) throws IOException {
            out.writeInt(acceptMessage.getSequenceNumber());
            out.writeLong(acceptMessage.getOpId().getLeastSignificantBits());
            out.writeLong(acceptMessage.getOpId().getMostSignificantBits());
            out.writeInt(acceptMessage.getOperation().length);
            out.writeBytes(acceptMessage.getOperation());
        }

        @Override
        public AcceptMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            UUID opId = new UUID(in.readLong(), in.readLong());
            int bytesLength = in.readInt();

            byte[] operation = new byte[bytesLength];
            for(int i = 0; i < bytesLength; i++)
                operation[i] = in.readByte();

            return new AcceptMessage(sequenceNumber, opId, operation);
        }
    };
}
