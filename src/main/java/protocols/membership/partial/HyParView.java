package protocols.membership.partial;

import babel.core.GenericProtocol;
import babel.exceptions.HandlerRegistrationException;
import babel.generic.ProtoMessage;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.partial.messages.*;
import protocols.membership.partial.timers.ShuffleTimer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class HyParView extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(HyParView.class);

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "HyParView";
    public static final short PROTOCOL_ID = 9969;

    @SuppressWarnings("FieldCanBeLocal")
    private final int k, c, arwl, prwl, ka, kp;
    private final int n = 10000; //TODO: QUANTO vale o n? Número de peers na rede?


    private final Host myself;

    private final int activeViewMaxSize;
    private final Set<Host> activeView;

    private final int passiveViewMaxSize;
    private final Set<Host> passiveView;

    private final int channelId; //Id of the created channel

    private final Random rnd ;

    public HyParView(Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.rnd = new Random();
        this.myself = myself;

        //Get some configurations from the Properties object
        this.k = Integer.parseInt(properties.getProperty("k", "5"));
        this.c = Integer.parseInt(properties.getProperty("c", "30"));
        this.arwl = Integer.parseInt(properties.getProperty("arwl", "4")); //TODO: QUAIS ESTES VALORES?
        this.prwl = Integer.parseInt(properties.getProperty("prwl", "4")); //TODO: QUAIS ESTES VALORES?
        this.ka = Integer.parseInt(properties.getProperty("ka", "1"));
        this.kp = Integer.parseInt(properties.getProperty("kp", "2"));

        activeViewMaxSize = (int) (Math.log(n) + c);
        activeView = new HashSet<>(activeViewMaxSize);

        passiveViewMaxSize = (int) (k * (Math.log(n) + c));
        passiveView = new HashSet<>(passiveViewMaxSize);

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
        registerMessageSerializer(channelId, NeighborMessage.MSG_ID, NeighborMessage.serializer);
        registerMessageSerializer(channelId, RejectMessage.MSG_ID, RejectMessage.serializer);
        registerMessageSerializer(channelId, ShuffleMessage.MSG_ID, ShuffleMessage.serializer);
        registerMessageSerializer(channelId, ShuffleReplyMessage.MSG_ID, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_ID, this::uponReceiveJoin, this::uponReceiveJoinFails);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_ID, this::uponReceiveForwardJoin, this::uponReceiveForwardJoinFails);
        registerMessageHandler(channelId, DisconnectMessage.MSG_ID, this::uponReceiveDisconnect, this::uponReceiveDisconnectFails);
        registerMessageHandler(channelId, NeighborMessage.MSG_ID, this::uponReceiveNeighbor, this::uponReceiveNeighborFails);
        registerMessageHandler(channelId, RejectMessage.MSG_ID, this::uponReceiveReject, this::uponReceiveRejectFails);
        registerMessageHandler(channelId, ShuffleMessage.MSG_ID, this::uponReceiveShuffle, this::uponReceiveShuffleFails);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_ID, this::uponReceiveReplyShuffle, this::uponReceiveShuffleReplyFails);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(ShuffleTimer.TIMER_ID, this::uponShuffleTimer);
        //registerTimerHandler(SampleTimer.TIMER_ID, this::uponSampleTimer);
        //registerTimerHandler(InfoTimer.TIMER_ID, this::uponInfoTime);

        /*-------------------- Register Channel Events ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);

        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
        //registerChannelEventHandler(channelId, ChannelMetrics.EVENT_ID, this::uponChannelMetrics);

    }

    @Override
    @SuppressWarnings("Duplicates") // PARA IGNORAR O CODIGO DUPLICADO (IF)
    public void init(Properties properties) {

        //Inform the dissemination protocol about the channel we created in the constructor
        triggerNotification(new ChannelCreated(channelId));

        //If there is a contact node, attempt to establish connection
        if (properties.containsKey("contact")) {
            try {
                String contact = properties.getProperty("contact");
                String[] hostElements = contact.split(":");
                Host contactHost = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));
                openConnection(contactHost);
                activeView.add(contactHost);

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
        if( isFull(activeView, activeViewMaxSize) )
            dropRandomElementFromActiveView();

        ForwardJoinMessage forwardJoinMessage = new ForwardJoinMessage(newNode, arwl);
        for ( Host n : activeView) {
            logger.debug("Send {} from {} to {}", forwardJoinMessage, myself, n);
            sendMessage(forwardJoinMessage, n);
        }
        openConnection(newNode);
        activeView.add(newNode);
    }

    private void uponReceiveForwardJoin(ForwardJoinMessage forwardJoinMessage, Host sender, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", forwardJoinMessage, sender);

        int timeToLive = forwardJoinMessage.getTTL();
        Host newNode = forwardJoinMessage.getNewNode();

        if(timeToLive == 0 || activeView.size() == 0)
            addNodeActiveView(newNode);
        else if (timeToLive == prwl)
            addNodePassiveView(newNode);

        ForwardJoinMessage newForwardJoinMessage = new ForwardJoinMessage(newNode, timeToLive - 1);
        for ( Host n : activeView)
            if(!n.equals(sender)) {
                logger.debug("Send {} from {} to {}", newForwardJoinMessage, myself, n);
                sendMessage(newForwardJoinMessage, n);
            }
    }

    private void dropRandomElementFromActiveView() {
        Host randomHost = getRandomFromSet(activeView);

        DisconnectMessage disconnectMessage = new DisconnectMessage();
        logger.debug("Send {} from {} to {}", disconnectMessage, myself, n);
        sendMessage(disconnectMessage, randomHost);

        activeView.remove(randomHost);
        closeConnection(randomHost);
        passiveView.add(randomHost);
    }

    private void addNodeActiveView(Host node) {
        if(node.equals(myself) && !activeView.contains(node)){
            if(isFull(activeView, activeViewMaxSize))
                dropRandomElementFromActiveView();
            openConnection(node);
            activeView.add(node);
        }
    }

    private void addNodePassiveView(Host node) {
        if(node.equals(myself) && !activeView.contains(node) && !passiveView.contains(node)){
            if(isFull(passiveView, passiveViewMaxSize))
                passiveView.remove( getRandomFromSet(passiveView));
            passiveView.add(node);
        }
    }

    private void uponReceiveDisconnect(DisconnectMessage disconnectMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", disconnectMessage, peer);
        if(activeView.contains(peer)){
            activeView.remove(peer);
            closeConnection(peer);
            addNodePassiveView(peer);
        }
    }

    private void uponReceiveNeighbor(NeighborMessage neighborMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", neighborMessage, peer);
        boolean isPriority = neighborMessage.isPriority();
        if (isPriority)
            addNodeActiveView(peer);
        else{
            if (!isFull(activeView, activeViewMaxSize)){
                //Aceita
                passiveView.remove(peer);
                openConnection(peer);
                activeView.add(peer);

            } else sendMessage(new RejectMessage(), peer);
        }
    }

    private void uponReceiveReject(RejectMessage rejectMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", rejectMessage, peer);
        passiveView.remove(peer); //Para não escolher o mesmo (?)
        attemptPassiveViewConnection();
        passiveView.add(peer);
    }

    private void uponReceiveShuffle(ShuffleMessage shuffleMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", shuffleMessage, peer);
        int newTtl = shuffleMessage.getTtl() - 1;
        shuffleMessage.setTtl(newTtl);

        if(newTtl > 0 && activeView.size() > 1){
            Host random = getRandomFromSet(activeView, peer); //Except peer
            sendMessage(shuffleMessage, random);
        }else{
            int numberOfNodesReceived = shuffleMessage.getPassiveViewSample().size();
            Set<Host> sample = getSampleFromSet(passiveView, numberOfNodesReceived);
            openConnection(peer); //Open Temporary connection
            sendMessage(new ShuffleReplyMessage(sample), peer);
            closeConnection(peer);//Close Temporary connection
        }
        //Then, both nodes integrate the elements they received in the Shuffle/ShuffleReply message into their passive views
        Set<Host> mergeSample = shuffleMessage.getPassiveViewSample();
        Set<Host> shuffleMessageActiveView = shuffleMessage.getActiveViewSample();
        mergeSample.addAll(shuffleMessageActiveView);

        //(naturally, they exclude their own identifier and nodes that are part of the active or passive views)
        removeMySelfAndViews(mergeSample);

        integrateElementsIntoPassiveView(mergeSample);
    }

    private void uponReceiveReplyShuffle(ShuffleReplyMessage shuffleReplyMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", shuffleReplyMessage, peer);

        Set<Host> mergeSample = shuffleReplyMessage.getSample();

        //(naturally, they exclude their own identifier and nodes that are part of the active or passive views)
        removeMySelfAndViews(mergeSample);

        integrateElementsIntoPassiveView(mergeSample);
    }

    //********************************* TIMERS FUNCTIONS ******************************************************

    private void uponShuffleTimer(ShuffleTimer shuffleTimer, long timerId) {
        logger.debug("Timer Shuffle");
        if (!activeView.isEmpty()) {
            Set<Host> activeViewSample = getSampleFromSet(activeView, ka);
            Set<Host> passiveViewSample = getSampleFromSet(passiveView, kp);

            Host randomNeighbor = getRandomFromSet(activeView);
            sendMessage(new ShuffleMessage(activeViewSample, passiveViewSample, arwl), randomNeighbor);
        }

    }

    //********************************* FAILS FUNCTIONS ******************************************************


    private void uponReceiveJoinFails(JoinMessage joinMessage, Host newNode, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", joinMessage, newNode, throwable);
    }

    private void uponReceiveForwardJoinFails(ForwardJoinMessage forwardJoinMessage, Host sender, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", forwardJoinMessage, sender, throwable);
    }

    private void uponReceiveDisconnectFails(DisconnectMessage disconnectMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", disconnectMessage, peer, throwable);
    }

    private void uponReceiveNeighborFails(NeighborMessage neighborMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", neighborMessage, peer, throwable);
    }

    private void uponReceiveRejectFails(RejectMessage rejectMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", rejectMessage, peer, throwable);
    }

    private void uponReceiveShuffleFails(ShuffleMessage shuffleMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", shuffleMessage, peer, throwable);
    }

    private void uponReceiveShuffleReplyFails(ShuffleReplyMessage shuffleReplyMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        //TODO: Fails
        logger.error("Message {} to {} failed, reason: {}", shuffleReplyMessage, peer, throwable);
    }

    /* ********************************* TCPChannel Events ********************************* */

    //If a connection is successfully established, this event is triggered. In this protocol, we want to add the
    //respective peer to the membership, and inform the Dissemination protocol via a notification.
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.debug("Connection to {} is up", event.getNode());
    }

    //If an established connection is disconnected, remove the peer from the membership and inform the Dissemination
    //protocol. Alternatively, we could do smarter things like retrying the connection X times.
    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection to {} is down cause {}", peer, event.getCause());

        if(activeView.contains(peer)) {
            activeView.remove(peer);
            attemptPassiveViewConnection();
        }
    }

    //If a connection fails to be established, this event is triggered. In this protocol, we simply remove from the
    //pending set. Note that this event is only triggered while attempting a connection, not after connection.
    //Thus the peer will be in the pending set, and not in the membership (unless something is very wrong with our code)
    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection to {} failed cause: {}", event.getNode(), event.getCause());

        activeView.remove(peer);
        passiveView.remove(peer);

        attemptPassiveViewConnection();
    }

    private void attemptPassiveViewConnection() {
        if(passiveView.size() > 0) {
            Host randomHost = getRandomFromSet(passiveView);

            openConnection(randomHost);

            boolean isPriority = false;
            if (activeView.size() == 0)
                isPriority = true;

            sendMessage(new NeighborMessage(isPriority), randomHost);
        }
    }

    //If someone established a connection to me, this event is triggered. In this protocol we do nothing with this event.
    //If we want to add the peer to the membership, we will establish our own outgoing connection.
    // (not the smartest protocol, but its simple)
    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Connection from {} is up", event.getNode());
    }

    //A connection someone established to me is disconnected.
    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    //********************************* AUXILIARIES FUNCTIONS ************************************************

    private <V> Set<V> getSampleFromSet(Set<V> set, int n) {
        List<V> setList = new ArrayList<>(set);
        Collections.shuffle(setList);
        return new HashSet<>(setList.subList(0, n));
    }

    private <V> V getRandomFromSet(Set<V> set) {
        List<V> setList = new ArrayList<>(set);
        return setList.get(rnd.nextInt(set.size()));
    }

    private <V> V getRandomFromSet(Set<V> set, Host notThis) {
        List<V> setList = new ArrayList<>(set);
        //noinspection SuspiciousMethodCalls
        setList.remove(notThis);
        return setList.get(rnd.nextInt(set.size()));
    }

    private <V> boolean isFull(Set<V> set, int maxSize){
        return set.size() == maxSize;
    }

    private <V> void removeRandomElements(Set<V> set, int nElements){
        List<V> setList = new ArrayList<>(set);
        for(int i = 0; i < nElements; i++)
            setList.remove(rnd.nextInt(setList.size()));
    }

    private void integrateElementsIntoPassiveView(Set<Host> mergeSample) {
        if(passiveView.size() + mergeSample.size() > passiveViewMaxSize)
            //it will remove identifiers at random.
            removeRandomElements(passiveView, mergeSample.size());
        else
            //Add all
            passiveView.addAll(mergeSample);
    }
    private void removeMySelfAndViews(Set<Host> mergeSample) {
        mergeSample.remove(myself);
        mergeSample.removeAll(passiveView);
        mergeSample.removeAll(activeView);
    }
}
