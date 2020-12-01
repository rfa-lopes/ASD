package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;

/*Made by Rodrigo*/
public class AcceptMessage extends ProtoMessage {

    public final static short MSG_CODE = 9024;

    public AcceptMessage() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "AcceptMessage{" +
                "}";
    }

    public static final ISerializer<AcceptMessage> serializer = new ISerializer<AcceptMessage>()  {
        @Override
        public void serialize(AcceptMessage acceptMessage, ByteBuf out) throws IOException {
        }

        @Override
        public AcceptMessage deserialize(ByteBuf in) throws IOException {
            return new AcceptMessage();
        }
    };
}
