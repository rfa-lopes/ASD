package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import java.io.IOException;

/*Made by Rodrigo*/
public class PrepareOkMessage extends ProtoMessage {

    public final static short MSG_CODE = 9023;

    public PrepareOkMessage() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "PrepareOkMessage{}";
    }

    public static final ISerializer<PrepareOkMessage> serializer = new ISerializer<PrepareOkMessage>()  {
        @Override
        public void serialize(PrepareOkMessage prepareOkMessage, ByteBuf out) throws IOException {
        }

        @Override
        public PrepareOkMessage deserialize(ByteBuf in) throws IOException {
            return new PrepareOkMessage();
        }
    };
}
