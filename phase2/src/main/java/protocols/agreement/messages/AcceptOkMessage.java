package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;
import java.util.UUID;

/*Made by Rodrigo*/
public class AcceptOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9025;

    private final int sequenceNumber;
    private final UUID opId;
    private final byte[] operation;

    public AcceptOkMessage(int sequenceNumber, UUID opId, byte[] operation) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
        this.opId = opId;
        this.operation = operation;
    }

    public AcceptOkMessage(int sequenceNumber) {
        super(MSG_CODE);
        this.sequenceNumber = sequenceNumber;
        this.opId = null;
        this.operation = null;
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
        return opId != null && operation != null ?
                "AcceptOkMessage{" +
                        "sequenceNumber= " + sequenceNumber +
                        ", opId= " + opId +
                        ", operation=" + Hex.encodeHexString(operation) +
                        "}"
                :
                "AcceptOkMessage{" +
                        "sequenceNumber= " + sequenceNumber +
                        "}";
    }

    public static final ISerializer<AcceptOkMessage> serializer = new ISerializer<AcceptOkMessage>()  {
        @Override
        public void serialize(AcceptOkMessage acceptMessage, ByteBuf out) throws IOException {
            out.writeInt(acceptMessage.getSequenceNumber());
            if(acceptMessage.getOpId() != null && acceptMessage.getOperation() != null) {
                out.writeLong(acceptMessage.getOpId().getLeastSignificantBits());
                out.writeLong(acceptMessage.getOpId().getMostSignificantBits());
                out.writeBytes(acceptMessage.getOperation());
            }
        }

        @Override
        public AcceptOkMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            if(in.readableBytes() > 0) {
                UUID opId = new UUID(in.readLong(), in.readLong());
                byte[] operation = in.array();
                return new AcceptOkMessage(sequenceNumber, opId, operation);
            }
            return new AcceptOkMessage(sequenceNumber);
        }
    };
}
