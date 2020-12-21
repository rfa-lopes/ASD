package protocols.app.requests;

import io.netty.buffer.ByteBuf;
import protocols.app.messages.RequestMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.nio.charset.StandardCharsets;

public class InstallStateRequest extends ProtoRequest {

    public static final short REQUEST_ID = 301;

    private byte[] state;

    public InstallStateRequest(byte[] state) {
        super(REQUEST_ID);
        this.state = state;
    }


    public byte[] getState() {
    	return this.state;
    }

    @Override
    public String toString() {
        return "CurrentStateReply{" +
                "number of bytes=" + state.length +
                '}';
    }

}
