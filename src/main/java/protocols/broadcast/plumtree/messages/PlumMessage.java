package protocols.broadcast.plumtree.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.broadcast.eagerpush.messages.GossipMessage;

import java.io.IOException;
import java.util.UUID;

public class PlumMessage extends GossipMessage {

    public static final short MSG_ID = 691;

    private int round;


    public PlumMessage(UUID mid, Host sender, short toDeliver, byte[] content, int round) {

        super(mid, sender, toDeliver, content);

        this.round = round;
    }

    public int getRound(){ return round; }

    public static ISerializer<PlumMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(PlumMessage plumMessage, ByteBuf out) throws IOException {
            out.writeLong(plumMessage.getMid().getMostSignificantBits());
            out.writeLong(plumMessage.getMid().getLeastSignificantBits());
            Host.serializer.serialize(plumMessage.getSender(), out);
            out.writeShort(plumMessage.getToDeliver());
            out.writeInt(plumMessage.getContent().length);
            out.writeInt(plumMessage.getRound());
            if (plumMessage.getContent().length > 0) {
                out.writeBytes(plumMessage.getContent());
            }
        }

        @Override
        public PlumMessage deserialize(ByteBuf in) throws IOException {
            long firstLong = in.readLong();
            long secondLong = in.readLong();
            UUID mid = new UUID(firstLong, secondLong);
            Host sender = Host.serializer.deserialize(in);
            short toDeliver = in.readShort();
            int size = in.readInt();
            int round = in.readInt();
            byte[] content = new byte[size];
            if (size > 0)
                in.readBytes(content);

            return new PlumMessage(mid, sender, toDeliver, content, round);
        }
    };

}
