package protocols.membership.partial.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ShuffleMessage extends ProtoMessage {

    public final static short MSG_ID = 9007;

    private Set<Host> activeViewSample;
    private Set<Host> passiveViewSample;
    private int ttl;

    public ShuffleMessage(Set<Host> activeViewSample, Set<Host> passiveViewSample, int ttl) {
        super(MSG_ID);
        this.activeViewSample = activeViewSample;
        this.passiveViewSample = passiveViewSample;
        this.ttl = ttl;
    }

    public Set<Host> getActiveViewSample() {
        return activeViewSample;
    }

    public Set<Host> getPassiveViewSample() {
        return passiveViewSample;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @Override
    public String toString() {
        return "ShuffleMessage{" +
                "activeViewSample=" + activeViewSample +
                " | passiveViewSample=" + passiveViewSample +
                " | TTL=" + ttl +
                '}';
    }

    public static ISerializer<ShuffleMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(ShuffleMessage shuffleMessage, ByteBuf out) throws IOException {

            Set<Host> activeViewSample = shuffleMessage.getActiveViewSample();
            Set<Host> passiveViewSample = shuffleMessage.getPassiveViewSample();
            int ttl = shuffleMessage.getTtl();

            out.writeInt(activeViewSample.size());
            for (Host h : activeViewSample)
                Host.serializer.serialize(h, out);

            out.writeInt(passiveViewSample.size());
            for (Host h : passiveViewSample)
                Host.serializer.serialize(h, out);

            out.writeInt(ttl);
        }

        @Override
        public ShuffleMessage deserialize(ByteBuf in) throws IOException {
            Set<Host> activeViewSample = getSetFromByteBuffer(in);
            Set<Host> passiveViewSample = getSetFromByteBuffer(in);
            int ttl = in.readInt();
            return new ShuffleMessage(activeViewSample, passiveViewSample, ttl);
        }
    };

    private static Set<Host> getSetFromByteBuffer(ByteBuf in) throws IOException {
        int viewSampleSize = in.readInt();
        Set<Host> viewSample = new HashSet<>(viewSampleSize, 1);
        for (int i = 0; i < viewSampleSize; i++)
            viewSample.add(Host.serializer.deserialize(in));
        return viewSample;
    }
}
