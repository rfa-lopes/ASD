package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class InformMembership extends ProtoMessage {

    public final static short MSG_CODE = 6905;

    private List<Host> membership;

    public InformMembership(List<Host> membership) {
        super(MSG_CODE);
        this.membership = membership;
    }

    public List<Host> getMembership() {
        return membership;
    }

    @Override
    public String toString() {
        return "InformMembership{" +
                "membership= " + this.membership +
                "}";
    }

    public static final ISerializer<InformMembership> serializer = new ISerializer<InformMembership>() {
        @Override
        public void serialize(InformMembership informMembership, ByteBuf out) throws IOException {
            out.writeInt(informMembership.getMembership().size());

            for(Host h : informMembership.getMembership()){
                Host.serializer.serialize(h, out);
            }
        }

        @Override
        public InformMembership deserialize(ByteBuf in) throws IOException {
            List<Host> membership = new LinkedList<>();

            int membershipSize = in.readInt();

            for (int i = 0; i < membershipSize; i++)
                membership.add(Host.serializer.deserialize(in));

            return new InformMembership(membership);
        }
    };
}
