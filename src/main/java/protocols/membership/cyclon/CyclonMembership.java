package protocols.membership.cyclon;

import babel.core.GenericProtocol;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.full.messages.SampleMessage;
import protocols.membership.full.timers.InfoTimer;
import protocols.membership.full.timers.SampleTimer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

public class CyclonMembership extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(CyclonMembership.class);

    public final static short PROTOCOL_ID = 100;
    public final static String PROTOCOL_NAME = "CyclonMembership";

    private final Host self;     //My own address/port

    private final Set<Host> membership; //Peers I am connected to

    private final int sampleTime; //param: timeout for samples
    private final int subsetSize; //param: maximum size of sample;

    private final Random rnd;

    private final int channelId; //Id of the created channel


    public CyclonMembership(Properties props, Host self) throws IOException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.self = self;
        this.membership = new HashSet<>();

        this.rnd = new Random();

        //Get some configurations from the Properties object
        this.subsetSize = Integer.parseInt(props.getProperty("sample_size", "6"));
        this.sampleTime = Integer.parseInt(props.getProperty("sample_time", "2000")); //2 seconds

        String cMetricsInterval = props.getProperty("channel_metrics_interval", "10000"); //10 seconds

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, cMetricsInterval); //The interval to receive channel metrics
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout
        channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, SampleMessage.MSG_ID, SampleMessage.serializer);

//        /*---------------------- Register Message Handlers -------------------------- */
//        registerMessageHandler(channelId, SampleMessage.MSG_ID, this::uponSample, this::uponMsgFail);
//
//        /*--------------------- Register Timer Handlers ----------------------------- */
//        registerTimerHandler(SampleTimer.TIMER_ID, this::uponSampleTimer);
//        registerTimerHandler(InfoTimer.TIMER_ID, this::uponInfoTime);
//
//        /*-------------------- Register Channel Events ------------------------------- */
//        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
//        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
//        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
//        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
//        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
//        registerChannelEventHandler(channelId, ChannelMetrics.EVENT_ID, this::uponChannelMetrics);

    }

    @Override
    public void init(Properties props) {
//
//        //Inform the dissemination protocol about the channel we created in the constructor
//        triggerNotification(new ChannelCreated(channelId));
//
//        //If there is a contact node, attempt to establish connection
//        if (props.containsKey("contact")) {
//            try {
//                String contact = props.getProperty("contact");
//                String[] hostElems = contact.split(":");
//                Host contactHost = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
//                //We add to the pending set until the connection is successful
//                pending.add(contactHost);
//                openConnection(contactHost);
//            } catch (Exception e) {
//                logger.error("Invalid contact on configuration: '" + props.getProperty("contacts"));
//                e.printStackTrace();
//                System.exit(-1);
//            }
//        }
//
//        //Setup the timer used to send samples (we registered its handler on the constructor)
//        setupPeriodicTimer(new SampleTimer(), this.sampleTime, this.sampleTime);
//
//        //Setup the timer to display protocol information (also registered handler previously)
//        int pMetricsInterval = Integer.parseInt(props.getProperty("protocol_metrics_interval", "10000"));
//        if (pMetricsInterval > 0)
//            setupPeriodicTimer(new InfoTimer(), pMetricsInterval, pMetricsInterval);
    }


}
