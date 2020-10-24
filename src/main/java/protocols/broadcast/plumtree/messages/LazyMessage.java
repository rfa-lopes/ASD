package protocols.broadcast.plumtree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.broadcast.eagerpush.messages.GossipMessage;

import java.io.IOException;
import java.util.UUID;

public class LazyMessage extends ProtoMessage {

    public static final short MSG_ID = 692;

    private final UUID mid;
    private final Host sender;

    private final short toDeliver;

    @Override
    public String toString() {
        return "LazyMessage{" +
                "mid=" + mid +
                '}';
    }

    public LazyMessage(UUID mid, Host sender, short toDeliver) {
        super(MSG_ID);
        this.mid = mid;
        this.sender = sender;
        this.toDeliver = toDeliver;
    }

    public Host getSender() {
        return sender;
    }

    public UUID getMid() {
        return mid;
    }

    public short getToDeliver() {
        return toDeliver;
    }

    public static ISerializer<LazyMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(LazyMessage lazyMessage, ByteBuf out) throws IOException {
            out.writeLong(lazyMessage.getMid().getMostSignificantBits());
            out.writeLong(lazyMessage.getMid().getLeastSignificantBits());
            Host.serializer.serialize(lazyMessage.getSender(), out);
            out.writeShort(lazyMessage.getToDeliver());
        }

        @Override
        public LazyMessage deserialize(ByteBuf in) throws IOException {
            long firstLong = in.readLong();
            long secondLong = in.readLong();
            UUID mid = new UUID(firstLong, secondLong);
            Host sender = Host.serializer.deserialize(in);
            short toDeliver = in.readShort();

            return new LazyMessage(mid, sender, toDeliver);
        }
    };

}
