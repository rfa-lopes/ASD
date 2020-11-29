package protocols.membership.cyclon.messages;

import babel.generic.ProtoMessage;
import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;
import protocols.membership.cyclon.utils.HostWithTime;

import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ShuffleRequest extends ProtoMessage {

    public final static short MSG_ID = 103;

    private final List<Host> sample;
    private final List<Integer> ages;




    public ShuffleRequest(List<Host> subset, List<Integer> ages2) {
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
        return "ShuffleRequest{" +
                "subset=" + sample +
                '}';
    }

    public static final ISerializer<ShuffleRequest> serializer = new ISerializer<ShuffleRequest>() {
        @Override
        public void serialize(ShuffleRequest shuffleRequest, ByteBuf out) throws IOException {
            out.writeInt(shuffleRequest.sample.size());
            out.writeInt(shuffleRequest.ages.size());
            for (Host h : shuffleRequest.sample)
                Host.serializer.serialize(h, out);

            for(Integer age : shuffleRequest.ages)
                out.writeInt(age);
        }

        @Override
        public ShuffleRequest deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            int ageSize = in.readInt();
            List<Host> subset = new LinkedList<>();
            for (int i = 0; i < size; i++)
                subset.add(Host.serializer.deserialize(in));

            List<Integer> ages2 = new LinkedList<>();
            for(int i = 0; i<ageSize; i++)
                ages2.add(in.readInt());

            return new ShuffleRequest(subset, ages2);
        }
    };


}
