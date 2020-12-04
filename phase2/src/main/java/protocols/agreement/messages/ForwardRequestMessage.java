package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class ForwardRequestMessage extends ProtoMessage {

    public final static short MSG_CODE = 9027;

    private final int sequenceNumber;

    private final UUID opId;

    private final byte[] operation;

    public ForwardRequestMessage(int sequenceNumber, UUID opId, byte[] operation) {
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
        return "ForwardRequest{" +
                "sequenceNumber: " + sequenceNumber +
                ", opId= " + opId +
                ", operation=" + Hex.encodeHexString(operation) +
                "}";
    }

    public static final ISerializer<ForwardRequestMessage> serializer = new ISerializer<ForwardRequestMessage>()  {
        @Override
        public void serialize(ForwardRequestMessage forwardRequest, ByteBuf out) throws IOException {
            out.writeInt(forwardRequest.getSequenceNumber());
            out.writeLong(forwardRequest.getOpId().getLeastSignificantBits());
            out.writeLong(forwardRequest.getOpId().getMostSignificantBits());
            out.writeBytes(forwardRequest.getOperation());
        }

        @Override
        public ForwardRequestMessage deserialize(ByteBuf in) throws IOException {
            int sequenceNumber = in.readInt();
            UUID opId = new UUID(in.readLong(), in.readLong());
            byte[] operation = in.array();
            return new ForwardRequestMessage(sequenceNumber, opId, operation);
        }
    };
}
