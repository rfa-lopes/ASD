package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ShuffleReplyMessage extends ProtoMessage {

    public final static short MSG_ID = 9008;
    private final Set<Host> sample;

    public ShuffleReplyMessage(Set<Host> sample) {
        super(MSG_ID);
        this.sample = sample;
    }

    public Set<Host> getSample() {
        return sample;
    }

    @Override
    public String toString() {
        return "ShuffleReplyMessage{" +
                "sample=" + sample +
                "}";
    }

    public static ISerializer<ShuffleReplyMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ShuffleReplyMessage shuffleReplyMessage, ByteBuf out) throws IOException {
            out.writeInt(shuffleReplyMessage.sample.size());
            for (Host h : shuffleReplyMessage.sample)
                Host.serializer.serialize(h, out);
        }

        @Override
        public ShuffleReplyMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Set<Host> subset = new HashSet<>(size);
            for (int i = 0; i < size; i++)
                subset.add(Host.serializer.deserialize(in));
            return new ShuffleReplyMessage(subset);
        }
    };
}
