package protocols.membership.cyclon.utils;

import io.netty.buffer.ByteBuf;
import network.ISerializer;
import network.data.Host;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class HostWithTime implements Serializable {

    public HostWithTime() {

    }

    public static ISerializer<HostWithTime> serializer = new ISerializer<HostWithTime>() {
        public void serialize(HostWithTime host2, ByteBuf out) {
            out.writeBytes(host2.getHost().getAddress().getAddress());
            out.writeShort(host2.host.getPort());
            out.writeInt(host2.age);
        }

        public HostWithTime deserialize(ByteBuf in) throws UnknownHostException {
            //gets age as well +4bytes
            byte[] addrBytes = new byte[8];
            in.readBytes(addrBytes);
            int port = in.readShort();
            int age = in.readInt();
            return new HostWithTime(InetAddress.getByAddress(addrBytes), addrBytes, port, age);
        }
    };

    private Host host;

    private int age;

    public HostWithTime(InetAddress byAddress, byte[] addrBytes, int port) throws UnknownHostException {

    }

    public HostWithTime(InetAddress byAddress, byte[] addrBytes, int port, int age) throws UnknownHostException {
        host = new Host(InetAddress.getByAddress(addrBytes), port);
        this.age = age;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
