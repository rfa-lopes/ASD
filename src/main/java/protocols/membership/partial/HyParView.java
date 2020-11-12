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
import protocols.membership.full.timers.InfoTimer;
import protocols.membership.partial.messages.*;
import protocols.membership.partial.timers.JoinTimer;
import protocols.membership.partial.timers.ShuffleTimer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class HyParView extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(HyParView.class);

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "HyParView";
    public static final short PROTOCOL_ID = 9069;

    @SuppressWarnings("FieldCanBeLocal")
    private final int k, c, arwl, prwl, ka, kp, n;

    private final int shuffleTime; //param: timeout for samples
    private final int joinTime;

    private final Host myself;

    private final int activeViewMaxSize;
    private final Set<Host> activeView;

    private final int passiveViewMaxSize;
    private final Set<Host> passiveView;

    private final Set<Host> contacts;

    private final int channelId; //Id of the created channel

    private final Random rnd;
    private long joinTimerId;

    private final Set<Host> temporaryConnections;

    public HyParView(Properties properties, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        rnd = new Random();
        this.myself = myself;
        shuffleTime =  Integer.parseInt(properties.getProperty("shuffleTime", "5000"));
        joinTime = Integer.parseInt(properties.getProperty("joinTime", "3000"));

        //Get some configurations from the Properties object
        k = Integer.parseInt(properties.getProperty("k", "6"));
        c = Integer.parseInt(properties.getProperty("c", "1"));
        arwl = Integer.parseInt(properties.getProperty("arwl", "6"));
        prwl = Integer.parseInt(properties.getProperty("prwl", "3"));
        ka = Integer.parseInt(properties.getProperty("ka", "3"));
        kp = Integer.parseInt(properties.getProperty("kp", "4"));
        n = Integer.parseInt(properties.getProperty("n", "10"));

        this.activeViewMaxSize = Integer.parseInt(properties.getProperty("activeMembershipSize", Integer.toString((int) (Math.log(n) + c))));
        this.activeView = new HashSet<>(activeViewMaxSize);

        passiveViewMaxSize = Integer.parseInt(properties.getProperty("passiveMembershipSize", Integer.toString((int) (k * (Math.log(n) + c)))));
        passiveView = new HashSet<>(passiveViewMaxSize);

        contacts = new HashSet<>();
        temporaryConnections = new HashSet<>();
        joinTimerId = -1;

        String cMetricsInterval = properties.getProperty("channel_metrics_interval", "10000"); //10 seconds

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, properties.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, properties.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, cMetricsInterval); //The interval to receive channel metrics
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "2500"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout
        channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, JoinMessage.MSG_ID, JoinMessage.serializer);
        registerMessageSerializer(channelId, JoinReplyMessage.MSG_ID, JoinReplyMessage.serializer);
        registerMessageSerializer(channelId, ForwardJoinMessage.MSG_ID, ForwardJoinMessage.serializer);
        registerMessageSerializer(channelId, DisconnectMessage.MSG_ID, DisconnectMessage.serializer);
        registerMessageSerializer(channelId, NeighborMessage.MSG_ID, NeighborMessage.serializer);
        registerMessageSerializer(channelId, RejectMessage.MSG_ID, RejectMessage.serializer);
        registerMessageSerializer(channelId, ShuffleMessage.MSG_ID, ShuffleMessage.serializer);
        registerMessageSerializer(channelId, ShuffleReplyMessage.MSG_ID, ShuffleReplyMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_ID, this::uponReceiveJoin, this::uponReceiveJoinFails);
        registerMessageHandler(channelId, JoinReplyMessage.MSG_ID, this::uponReceiveJoinReply, this::uponReceiveJoinReplyFails);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_ID, this::uponReceiveForwardJoin, this::uponReceiveForwardJoinFails);
        registerMessageHandler(channelId, DisconnectMessage.MSG_ID, this::uponReceiveDisconnect, this::uponReceiveDisconnectFails);
        registerMessageHandler(channelId, NeighborMessage.MSG_ID, this::uponReceiveNeighbor, this::uponReceiveNeighborFails);
        registerMessageHandler(channelId, RejectMessage.MSG_ID, this::uponReceiveReject, this::uponReceiveRejectFails);
        registerMessageHandler(channelId, ShuffleMessage.MSG_ID, this::uponReceiveShuffle, this::uponReceiveShuffleFails);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_ID, this::uponReceiveReplyShuffle, this::uponReceiveShuffleReplyFails);

        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(JoinTimer.TIMER_ID, this::uponJoinTimer);
        registerTimerHandler(ShuffleTimer.TIMER_ID, this::uponShuffleTimer);
        registerTimerHandler(InfoTimer.TIMER_ID, this::uponInfoTime);

        /*-------------------- Register Channel Events ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);

        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
        registerChannelEventHandler(channelId, ChannelMetrics.EVENT_ID, this::uponChannelMetrics);

    }

    @Override
    // Ignore duplicate code (IF)
    public void init(Properties properties) {

        //Inform the dissemination protocol about the channel we created in the constructor
        triggerNotification(new ChannelCreated(channelId));

        //If there is a contact node, attempt to establish connection
        if (properties.containsKey("contact")) {
            try {
                String contactsProperty = properties.getProperty("contact");
                String[] hosts = contactsProperty.split(",");

                String[] hostElements;
                for(String host : hosts){
                    hostElements = host.split(":");
                    contacts.add(new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1])));
                }

                joinMembership();

            } catch (Exception e) {
                logger.error("Invalid contacts on configuration: '" + properties.getProperty("contact"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        //Setup the timer used to send shuffle request (we registered its handler on the constructor)
        setupPeriodicTimer(new ShuffleTimer(), this.shuffleTime, this.shuffleTime);

        //Setup the timer to display protocol information (also registered handler previously)
        int pMetricsInterval = Integer.parseInt(properties.getProperty("protocol_metrics_interval", "10000"));
        if (pMetricsInterval > 0)
            setupPeriodicTimer(new InfoTimer(), pMetricsInterval, pMetricsInterval);
    }

    //********************************* JOIN *********************************
    private void joinMembership() {
        if(!contacts.isEmpty()) {
            Host contactHost = getRandomFromSet(contacts);
            JoinMessage joinMessage = new JoinMessage();
            logger.debug("Send {} from {} to {}", joinMessage, myself, contactHost);

            //temporaryConnections.add(contactHost);
            openConnection(contactHost);
            sendMessage(joinMessage, contactHost);

            if(joinTimerId == -1) {
                logger.debug("Setup Join Timer");
                joinTimerId = setupPeriodicTimer(new JoinTimer(), this.joinTime, this.joinTime);
            }
        }
    }

    private void uponReceiveJoin(JoinMessage joinMessage, Host newNode, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", joinMessage, newNode);
        if( isFull(activeView, activeViewMaxSize) )
            dropRandomElementFromActiveView();

        JoinReplyMessage joinReply = new JoinReplyMessage();
        activeView.add(newNode);
        logger.debug("Send {} from {} to {}", joinReply, myself, newNode);
        openConnection(newNode);
        sendMessage(joinReply, newNode);

        ForwardJoinMessage forwardJoinMessage = new ForwardJoinMessage(newNode, arwl);
        for ( Host n : activeView) {
            if(!n.equals(newNode)){
                logger.debug("Send {} from {} to {}", forwardJoinMessage, myself, n);
                openConnection(n);
                sendMessage(forwardJoinMessage, n);
            }
        }
    }

    private void uponReceiveJoinReply(JoinReplyMessage joinReplyMessage, Host contactHost, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", joinReplyMessage, contactHost);
        activeView.add(contactHost);
        //temporaryConnections.remove(contactHost);
        logger.debug("Join Timer with timerID={} canceled.", joinTimerId);
        cancelTimer(joinTimerId);
    }

    private void uponReceiveForwardJoin(ForwardJoinMessage forwardJoinMessage, Host sender, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", forwardJoinMessage, sender);

        int timeToLive = forwardJoinMessage.getTTL();

        if(timeToLive < 0)
            return;

        Host newNode = forwardJoinMessage.getNewNode();

        if(timeToLive == 0 || activeView.size() == 0) //PAPER says ==1 (??)
            addNodeActiveView(newNode);
        else if (timeToLive == prwl)
            addNodePassiveView(newNode);

        if(!activeView.isEmpty()) {
            ForwardJoinMessage newForwardJoinMessage = new ForwardJoinMessage(newNode, timeToLive - 1);
            Host randomHost = getRandomFromSet(activeView, sender);

            if(randomHost == null)
                return ;

            logger.debug("Send {} from {} to {}", newForwardJoinMessage, myself, randomHost);
            openConnection(randomHost);
            sendMessage(newForwardJoinMessage, randomHost);
        }
    }

    private void dropRandomElementFromActiveView() {
        Host randomHost = getRandomFromSet(activeView);

        DisconnectMessage disconnectMessage = new DisconnectMessage();
        logger.debug("Send {} from {} to {}", disconnectMessage, myself, randomHost);
        openConnection(randomHost);
        sendMessage(disconnectMessage, randomHost);

        activeView.remove(randomHost);
        passiveView.add(randomHost);
    }

    private void addNodeActiveView(Host node) {
        if(!node.equals(myself) && !activeView.contains(node)){
            if(isFull(activeView, activeViewMaxSize))
                dropRandomElementFromActiveView();
            activeView.add(node);
        }
    }

    private void addNodePassiveView(Host node) {
        if(!node.equals(myself) && !activeView.contains(node) && !passiveView.contains(node)){
            if(isFull(passiveView, passiveViewMaxSize))
                passiveView.remove( getRandomFromSet(passiveView) );
            passiveView.add(node);
        }
    }

    private void uponReceiveDisconnect(DisconnectMessage disconnectMessage, Host peer, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", disconnectMessage, peer);
        if(activeView.contains(peer)){
            activeView.remove(peer);
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
                activeView.add(peer);

            } else {
                openConnection(peer);
                sendMessage(new RejectMessage(), peer);
            }
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
            if(random != null) {
                openConnection(random);
                sendMessage(shuffleMessage, random);
            }
        }else{
            int numberOfNodesReceived = shuffleMessage.getPassiveViewSample().size();
            Set<Host> sample = getSampleFromSet(passiveView, numberOfNodesReceived);

            //Open Temporary connection
            temporaryConnections.add(peer);
            openConnection(peer);
            sendMessage(new ShuffleReplyMessage(sample), peer);
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

    private void uponJoinTimer(JoinTimer v, long timerId) {
        logger.debug("Join Timer expired. Try again...");
        joinMembership();
    }

    private void uponShuffleTimer(ShuffleTimer shuffleTimer, long timerId) {
        logger.debug("Timer Shuffle...");
        if (!activeView.isEmpty()) {
            Set<Host> activeViewSample = getSampleFromSet(activeView, ka);
            Set<Host> passiveViewSample = getSampleFromSet(passiveView, kp);
            Host randomNeighbor = getRandomFromSet(activeView);
            ShuffleMessage shuffleMessage = new ShuffleMessage(activeViewSample, passiveViewSample, arwl);
            logger.debug("Send {} from {} to {}", shuffleMessage, myself, randomNeighbor);
            openConnection(randomNeighbor);
            sendMessage(shuffleMessage, randomNeighbor);
        }

    }

    //********************************* FAILS FUNCTIONS ******************************************************

    private void uponReceiveJoinFails(JoinMessage joinMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.debug("Message {} to {} failed, reason: {}", joinMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveJoinReplyFails(JoinReplyMessage joinReplyMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.debug("Message {} to {} failed, reason: {}", joinReplyMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveForwardJoinFails(ForwardJoinMessage forwardJoinMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", forwardJoinMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveDisconnectFails(DisconnectMessage disconnectMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", disconnectMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveNeighborFails(NeighborMessage neighborMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", neighborMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveRejectFails(RejectMessage rejectMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", rejectMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveShuffleFails(ShuffleMessage shuffleMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", shuffleMessage, peer, throwable);
        connectionFailed(peer);
    }

    private void uponReceiveShuffleReplyFails(ShuffleReplyMessage shuffleReplyMessage, Host peer, short destProto, Throwable throwable, int channelId) {
        logger.error("Message {} to {} failed, reason: {}", shuffleReplyMessage, peer, throwable);
        connectionFailed(peer);
    }

    /* ********************************* TCPChannel Events ********************************* */

    //If a connection is successfully established, this event is triggered. In this protocol, we want to add the
    //respective peer to the membership, and inform the Dissemination protocol via a notification.
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection to {} is up", peer);

        if(temporaryConnections.contains(peer)){
            //Close Temporary connections
            logger.debug("Temporary Out Connection down with peer: {}", peer);
            temporaryConnections.remove(peer);
            closeConnection(peer);
        }

    }

    //If an established connection is disconnected, remove the peer from the membership and inform the Dissemination
    //protocol. Alternatively, we could do smarter things like retrying the connection X times.
    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection to {} is down cause {}", peer, event.getCause());

        if(activeView.contains(peer)) {
            logger.debug("Remove {} from Active View", peer);
            activeView.remove(peer);
            attemptPassiveViewConnection();
            passiveView.add(peer);
        }
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection to {} failed cause: {}", peer, event.getCause());
        activeView.remove(peer);
        passiveView.remove(peer);
        attemptPassiveViewConnection();
    }

    private void attemptPassiveViewConnection() {
        if(passiveView.size() > 0) {
            Host randomHost = getRandomFromSet(passiveView);

            boolean isPriority = false;
            if (activeView.size() == 0)
                isPriority = true;

            logger.debug("Attempt passive view connection with: {}", randomHost);
            passiveView.remove(randomHost);
            activeView.add(randomHost);
            openConnection(randomHost);
            sendMessage(new NeighborMessage(isPriority), randomHost);
        }
    }

    //If someone established a connection to me, this event is triggered. In this protocol we do nothing with this event.
    //If we want to add the peer to the membership, we will establish our own outgoing connection.
    // (not the smartest protocol, but its simple)
    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        Host peer = event.getNode();
        logger.debug("Connection from {} is up", peer);
    }

    //A connection someone established to me is disconnected.
    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.debug("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    //********************************* AUXILIARIES FUNCTIONS ************************************************

    private <V> Set<V> getSampleFromSet(Set<V> set, int n) {
        List<V> setList = new ArrayList<>(set);
        Collections.shuffle(setList);
        return new HashSet<>(setList.subList(0, Math.min(n, set.size())));
    }

    private <V> V getRandomFromSet(Set<V> set) {
        return getRandomFromSet(set, null);
    }

    private <V> V getRandomFromSet(Set<V> set, Host notThis) {
        List<V> setList = new ArrayList<>(set);
        if(notThis != null)
            //noinspection SuspiciousMethodCalls
            setList.remove(notThis);
        if(setList.size() == 0)
            return null;
        return setList.get(rnd.nextInt(setList.size()));
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

    private void connectionFailed(Host peer){
        passiveView.remove(peer);
        if(activeView.contains(peer)) {
            logger.debug("Remove {} from Active View", peer);
            closeConnection(peer);
            activeView.remove(peer);
            attemptPassiveViewConnection();
        }
    }

    /* --------------------------------- Metrics ---------------------------- */

    //If we setup the InfoTimer in the constructor, this event will be triggered periodically.
    //We are simply printing some information to present during runtime.
    private void uponInfoTime(InfoTimer timer, long timerId) {
        StringBuilder sb = new StringBuilder("Membership Metrics:\n");
        sb.append("\n");
        sb.append("Contacts: ").append(contacts).append("\n");
        sb.append("ActiveView: ").append(activeView).append("\n");
        sb.append("PassiveView: ").append(passiveView).append("\n");
        sb.append("activeViewMaxSize: ").append(activeViewMaxSize).append("\n");
        sb.append("passiveViewMaxSize: ").append(passiveViewMaxSize).append("\n");
        sb.append("\n");
        //getMetrics returns an object with the number of events of each type processed by this protocol.
        //It may or may not be useful to you, but at least you know it exists.
        sb.append(getMetrics());
        logger.info(sb);
    }

    //If we passed a value >0 in the METRICS_INTERVAL_KEY property of the channel, this event will be triggered
    //periodically by the channel. This is NOT a protocol timer, but a channel event.
    //Again, we are just showing some of the information you can get from the channel, and use how you see fit.
    //"getInConnections" and "getOutConnections" returns the currently established connection to/from me.
    //"getOldInConnections" and "getOldOutConnections" returns connections that have already been closed.
    @SuppressWarnings("DuplicatedCode")
    private void uponChannelMetrics(ChannelMetrics event, int channelId) {
        StringBuilder sb = new StringBuilder("Channel Metrics:\n");
        sb.append("In channels:\n");
        event.getInConnections().forEach(c -> sb.append(String.format("\t%s: msgOut=%s (%s) msgIn=%s (%s)\n",
                c.getPeer(), c.getSentAppMessages(), c.getSentAppBytes(), c.getReceivedAppMessages(),
                c.getReceivedAppBytes())));
        event.getOldInConnections().forEach(c -> sb.append(String.format("\t%s: msgOut=%s (%s) msgIn=%s (%s) (old)\n",
                c.getPeer(), c.getSentAppMessages(), c.getSentAppBytes(), c.getReceivedAppMessages(),
                c.getReceivedAppBytes())));
        sb.append("Out channels:\n");
        event.getOutConnections().forEach(c -> sb.append(String.format("\t%s: msgOut=%s (%s) msgIn=%s (%s)\n",
                c.getPeer(), c.getSentAppMessages(), c.getSentAppBytes(), c.getReceivedAppMessages(),
                c.getReceivedAppBytes())));
        event.getOldOutConnections().forEach(c -> sb.append(String.format("\t%s: msgOut=%s (%s) msgIn=%s (%s) (old)\n",
                c.getPeer(), c.getSentAppMessages(), c.getSentAppBytes(), c.getReceivedAppMessages(),
                c.getReceivedAppBytes())));
        sb.setLength(sb.length() - 1);
        logger.info(sb);
    }

    /* --------------------------------- Utils ---------------------------- */

    public Set<Host> getNeighbours() {
        return new HashSet<>(activeView);
    }
}
