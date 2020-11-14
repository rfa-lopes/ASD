package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class ForwardJoinMessage extends ProtoMessage {
    public final static short MSG_ID = 9002;
    private final Host newNode;
    private final int ttl;

    public ForwardJoinMessage(Host newNode, int ttl) {
        super(MSG_ID);
        this.newNode = newNode;
        this.ttl = ttl;
    }

    public Host getNewNode() {
        return newNode;
    }

    public int getTTL(){
        return ttl;
    }

    @Override
    public String toString() {
        return "ForwardJoinMessage{" +
                "newNode=" + newNode +
                " | TTL=" + ttl +
                '}';
    }

    public static ISerializer<ForwardJoinMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ForwardJoinMessage forwardJoinMessage, ByteBuf out) throws IOException {
            out.writeInt(forwardJoinMessage.getTTL());
            Host.serializer.serialize(forwardJoinMessage.getNewNode(), out);
        }

        @Override
        public ForwardJoinMessage deserialize(ByteBuf in) throws IOException {
            int ttl = in.readInt();
            return new ForwardJoinMessage(Host.serializer.deserialize(in), ttl);
        }
    };
}
