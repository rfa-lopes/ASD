package protocols.membership.cyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.membership.cyclon.utils.HostWithTime;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ShuffleReply extends ProtoMessage {

    public final static short MSG_ID = 102;

    private final List<Host> sample;
    private final List<Integer> ages;

    public ShuffleReply(List<Host> subset, List<Integer> ages2) {
        super(MSG_ID);
        sample = subset;
        ages = ages2;
    }

    public List<Host> getSample() {
        return sample;
    }

    public List<Integer> getAges() {
        return ages;
    }

    @Override
    public String toString() {
        return "ShuffleReply{" +
                "subset=" + sample +
                '}';
    }

    public static final ISerializer<ShuffleReply> serializer = new ISerializer<ShuffleReply>() {
        @Override
        public void serialize(ShuffleReply shuffleReply, ByteBuf out) throws IOException {
            out.writeInt(shuffleReply.sample.size());
            out.writeInt(shuffleReply.ages.size());
            for (Host h : shuffleReply.sample)
                Host.serializer.serialize(h, out);

            for(Integer age : shuffleReply.ages)
                out.writeInt(age);
        }

        @Override
        public ShuffleReply deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            int ageSize = in.readInt();
            List<Host> subset = new LinkedList<>();
            for (int i = 0; i < size; i++)
                subset.add(Host.serializer.deserialize(in));

            List<Integer> ages2 = new LinkedList<>();
            for(int i = 0; i<ageSize; i++)
                ages2.add(in.readInt());

            return new ShuffleReply(subset, ages2);
        }
    };

}
