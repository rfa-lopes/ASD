package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class DecidedMessage extends ProtoMessage {

    public final static short MSG_CODE = 203;

    private byte[] state;
    private int instance;
    private UUID opId;
    private byte[] operation;

    public DecidedMessage(byte[] state, int instance, UUID opId, byte[] operation) {
        super(MSG_CODE);
        this.instance = instance;
        this.opId = opId;
        this.operation = operation;
        this.state = state;
    }

    public byte[] getState() {
        return state;
    }

    public int getInstance() {
        return instance;
    }

    public UUID getOpId() {
        return opId;
    }

    public byte[] getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "DecidedMessage{" +
                "state= " + this.state +
                "instance= " + this.instance +
                "opId= " + this.opId +
                "operation= " + this.operation +
                "}";
    }

    public static final ISerializer<DecidedMessage> serializer = new ISerializer<DecidedMessage>() {
        @Override
        public void serialize(DecidedMessage decidedMessage, ByteBuf out) throws IOException {
            out.writeInt(decidedMessage.getInstance());

            out.writeLong(decidedMessage.getOpId().getLeastSignificantBits());
            out.writeLong(decidedMessage.getOpId().getMostSignificantBits());

            out.writeInt(decidedMessage.getOperation().length);
            out.writeBytes(decidedMessage.getOperation());

            out.writeInt(decidedMessage.getState().length);
            out.writeBytes(decidedMessage.getState());
        }

        @Override
        public DecidedMessage deserialize(ByteBuf in) throws IOException {

            int instance = in.readInt();

            UUID opId = new UUID(in.readLong(), in.readLong());

            byte[] operation = new byte[in.readInt()];
            in.readBytes(operation);

            byte[] state = new byte[in.readInt()];
            in.readBytes(state);

            return new DecidedMessage(state, instance, opId, operation);
        }
    };
}
