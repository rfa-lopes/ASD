package protocols.membership.partial;

import babel.core.GenericProtocol;
import babel.exceptions.HandlerRegistrationException;
import channel.tcp.TCPChannel;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.partial.messages.DisconnectMessage;
import protocols.membership.partial.messages.ForwardJoinMessage;
import protocols.membership.partial.messages.JoinMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class HyParView extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(HyParView.class);

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "HyParView";
    public static final short PROTOCOL_ID = 175;

    private final int k, c, arwl, prwl;
    private final int n = 10000; //TODO: QUANTO vale o n? Número de peers na rede?


    private Host myself;

    private int smallActiveViewMaxSize;
    private Set<Host> smallActiveView;

    private int largePassiveViewMaxSize;
    private Set<Host> largePassiveView;

    private final int channelId; //Id of the created channel

    private Random rnd ;

    public HyParView(Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.rnd = new Random();
        this.myself = myself;

        //Get some configurations from the Properties object
        this.k = Integer.parseInt(properties.getProperty("k", "5"));
        this.c = Integer.parseInt(properties.getProperty("c", "30"));
        this.arwl = Integer.parseInt(properties.getProperty("arwl", "4")); //TODO: QUAIS ESTES VALORES?
        this.prwl = Integer.parseInt(properties.getProperty("arwl", "4")); //TODO: QUAIS ESTES VALORES?

        smallActiveViewMaxSize  = (int) (Math.log(n) + c);
        smallActiveView = new HashSet<>(smallActiveViewMaxSize);

        largePassiveViewMaxSize  = (int) (k * (Math.log(n) + c));
        largePassiveView = new HashSet<>(largePassiveViewMaxSize);

        String cMetricsInterval = properties.getProperty("channel_metrics_interval", "10000"); //10 seconds

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, properties.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, properties.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, cMetricsInterval); //The interval to receive channel metrics
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout
        channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, JoinMessage.MSG_ID, JoinMessage.serializer);
        registerMessageSerializer(channelId, ForwardJoinMessage.MSG_ID, ForwardJoinMessage.serializer);
        registerMessageSerializer(channelId, DisconnectMessage.MSG_ID, DisconnectMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_ID, this::uponReceiveJoin, this::uponReceiveJoinFails);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_ID, this::uponReceiveForwardJoin, this::uponReceiveForwardJoinFails);
        registerMessageHandler(channelId, DisconnectMessage.MSG_ID, this::uponReceiveDisconnect, this::uponReceiveDisconnectFails);

        /*--------------------- Register Timer Handlers ----------------------------- */
        //registerTimerHandler(SampleTimer.TIMER_ID, this::uponSampleTimer);
        //registerTimerHandler(InfoTimer.TIMER_ID, this::uponInfoTime);

    }

    @Override
    @SuppressWarnings("Duplicates") // PARA IGNORAR O CODIGO DUPLICADO (IF)
    public void init(Properties properties) throws HandlerRegistrationException, IOException {

        //Inform the dissemination protocol about the channel we created in the constructor
        triggerNotification(new ChannelCreated(channelId));

        //TODO: O QUE FAZER AQUI?

        //If there is a contact node, attempt to establish connection
        if (properties.containsKey("contact")) {
            try {
                String contact = properties.getProperty("contact");
                String[] hostElems = contact.split(":");
                Host contactHost = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
                smallActiveView.add(contactHost);
                openConnection(contactHost);//TODO: TENHO QUE FAZER ISTO SEMPRE PARA MANDAR UMA MENSAGEM?

                JoinMessage joinMessage = new JoinMessage();
                logger.debug("Send {} from {} to {}", joinMessage, myself, contactHost);
                sendMessage(joinMessage, contactHost);

            } catch (Exception e) {
                logger.error("Invalid contact on configuration: '" + properties.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private void uponReceiveJoin(JoinMessage joinMessage, Host newNode, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", joinMessage, newNode);
        if( isFull(smallActiveView, smallActiveViewMaxSize) )
            dropRandomElementFromActiveView();

        ForwardJoinMessage forwardJoinMessage = new ForwardJoinMessage(newNode, arwl);
        for ( Host n : smallActiveView ) {
            logger.debug("Send {} from {} to {}", forwardJoinMessage, myself, n);
            sendMessage(forwardJoinMessage, n);
        }
        smallActiveView.add(newNode);
    }

    private void uponReceiveForwardJoin(ForwardJoinMessage forwardJoinoinMessage, Host sender, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", forwardJoinoinMessage, sender);
        int timeToLive = forwardJoinoinMessage.getArwl();
        Host newNode = forwardJoinoinMessage.getNewNode();
        if(timeToLive == 0 || smallActiveView.size() == 0)
            addNodeActiveView(newNode);
        else
        if(timeToLive == prwl)
            addNodePassiveView(newNode);
        //TODO: n ← n ∈ activeView and n != sender (É para fazer um ciclo?)

        ForwardJoinMessage forwardJoinMessage = new ForwardJoinMessage(newNode, timeToLive - 1);
        for ( Host n : smallActiveView )
            if(!n.equals(sender)) {
                logger.debug("Send {} from {} to {}", forwardJoinMessage, myself, n);
                sendMessage(forwardJoinMessage, n);
            }
    }

    private void dropRandomElementFromActiveView() {
        Host randomHost = getRandomFromSet(smallActiveView);

        DisconnectMessage disconnectMessage = new DisconnectMessage();
        logger.debug("Send {} from {} to {}", disconnectMessage, myself, n);
        sendMessage(disconnectMessage, randomHost);

        smallActiveView.remove(randomHost);
        largePassiveView.add(randomHost);
    }

    private void addNodeActiveView(Host node) {
        if(node.equals(myself) && !smallActiveView.contains(node)){
            if(isFull(smallActiveView, smallActiveViewMaxSize))
                dropRandomElementFromActiveView();
            smallActiveView.add(node);
        }
    }

    private void addNodePassiveView(Host node) {
        if(node.equals(myself) && !smallActiveView.contains(node) && !largePassiveView.contains(node)){
            if(isFull(largePassiveView, largePassiveViewMaxSize))
                largePassiveView.remove( getRandomFromSet(largePassiveView));
            largePassiveView.add(node);
        }
    }

    private void uponReceiveDisconnect(DisconnectMessage disconnectMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", disconnectMessage, peer);
        if(smallActiveView.contains(peer)){
            smallActiveView.remove(peer);
            addNodePassiveView(peer);
        }
    }

    //********************************* FAILS FUNCTIONS ******************************************************


    private void uponReceiveJoinFails(JoinMessage joinMessage, Host newNode, short destProto, Throwable throwable, int channelId) {
        //TODO
        logger.error("Message {} to {} failed, reason: {}", joinMessage, newNode, throwable);
    }

    private void uponReceiveForwardJoinFails(ForwardJoinMessage forwardJoinoinMessage, Host sender, short destProto, Throwable throwable, int channelId) {
        //TODO
        logger.error("Message {} to {} failed, reason: {}", forwardJoinoinMessage, sender, throwable);
    }

    private void uponReceiveDisconnectFails(DisconnectMessage disconnectMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO
        logger.error("Message {} to {} failed, reason: {}", disconnectMessage, peer, throwable);
    }

    //********************************* AUXILIARIES FUNCTIONS ************************************************

    private <V> V getRandomFromSet(Set<V> set) {
        List<V> setList = new ArrayList<>(set);
        return setList.get(rnd.nextInt());
    }

    private <V> boolean isFull(Set<V> set, int maxSize){
        return set.size() == maxSize;
    }
}
