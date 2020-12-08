package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import protocols.agreement.messages.AcceptMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class RequestMembership extends ProtoMessage {

    public static final short MSG_CODE = 6906;

    public RequestMembership() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "OrderRequest{" +
                '}';
    }

    public static final ISerializer<RequestMembership> serializer = new ISerializer<RequestMembership>()  {
        @Override
        public void serialize(RequestMembership requestMembership, ByteBuf out) throws IOException {
        }

        @Override
        public RequestMembership deserialize(ByteBuf in) throws IOException {
            return new RequestMembership();
        }
    };

}
