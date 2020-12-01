package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;

/*Made by Rodrigo*/
public class AcceptOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9025;

    public AcceptOkMessage() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "AcceptOkMessage{" +
                "}";
    }

    public static final ISerializer<AcceptOkMessage> serializer = new ISerializer<AcceptOkMessage>()  {
        @Override
        public void serialize(AcceptOkMessage acceptMessage, ByteBuf out) throws IOException {
        }

        @Override
        public AcceptOkMessage deserialize(ByteBuf in) throws IOException {
            return new AcceptOkMessage();
        }
    };
}
