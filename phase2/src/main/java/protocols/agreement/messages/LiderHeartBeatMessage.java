package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class LiderHeartBeatMessage extends ProtoMessage {

    public final static short MSG_CODE = 9033;

    public LiderHeartBeatMessage() {
        super(MSG_CODE);

    }


    @Override
    public String toString() {
        return "LiderHeartBeat{}";
    }

    public static final ISerializer<LiderHeartBeatMessage> serializer = new ISerializer<LiderHeartBeatMessage>()  {
        @Override
        public void serialize(LiderHeartBeatMessage liderHeartBeatMessage, ByteBuf out) throws IOException {
        }

        @Override
        public LiderHeartBeatMessage deserialize(ByteBuf in) throws IOException {
            return new LiderHeartBeatMessage();
        }
    };

}
