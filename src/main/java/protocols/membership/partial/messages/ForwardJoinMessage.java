package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;

public class ForwardJoinMessage extends ProtoMessage {
    public final static short MSG_ID = 9002;
    private final Host newNode;
    private final int arwl;

    public ForwardJoinMessage(Host newNode, int arwl) {
        super(MSG_ID);
        this.newNode = newNode;
        this.arwl = arwl;
    }

    public Host getNewNode() {
        return newNode;
    }

    public int getArwl(){
        return arwl;
    }

    @Override
    public String toString() {
        return "ForwardJoinMessage{" +
                "newNode=" + newNode +
                " | ARWL=" + arwl +
                '}';
    }

    public static ISerializer<ForwardJoinMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ForwardJoinMessage forwardJoinMessage, ByteBuf out) throws IOException {
            out.writeInt(forwardJoinMessage.getArwl());
            Host.serializer.serialize(forwardJoinMessage.getNewNode(), out);
        }

        @Override
        public ForwardJoinMessage deserialize(ByteBuf in) throws IOException {
            int arwl = in.readInt();
            return new ForwardJoinMessage(Host.serializer.deserialize(in), arwl);
        }
    };
}
