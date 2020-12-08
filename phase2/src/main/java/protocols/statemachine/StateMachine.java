package protocols.statemachine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.MultiPaxos;
import protocols.agreement.Paxos;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.ProposeRequest;
import protocols.app.requests.CurrentStateReply;
import protocols.app.requests.CurrentStateRequest;
import protocols.app.utils.Operation;
import protocols.statemachine.messages.InformMembership;
import protocols.statemachine.messages.RequestMembership;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.requests.OrderRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This is NOT fully functional StateMachine implementation.
 * This is simply an example of things you can do, and can be used as a starting point.
 * <p>
 * You are free to change/delete anything in this class, including its fields.
 * The only thing that you cannot change are the notifications/requests between the StateMachine and the APPLICATION
 * You can change the requests/notification between the StateMachine and AGREEMENT protocol, however make sure it is
 * coherent with the specification shown in the project description.
 * <p>
 * Do not assume that any logic implemented here is correct, think for yourself!
 */
public class StateMachine extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(StateMachine.class);

    private final int MAX_INSTANCES = 1000;

    private enum State {JOINING, ACTIVE}

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "StateMachine";
    public static final short PROTOCOL_ID = 200;

    private final Host self;     //My own address/port
    private final int channelId; //Id of the created channel

    private State state;
    private List<Host> membership;
    private int nextInstance;

    private int executedOps;
    private final Map<String, byte[]> data;
    private byte[] cumulativeHash;

    private List<UUID> opsToBe;
    private Map<UUID, Operation> operationMap;
    private String agreement;

    private boolean hasState;
    private boolean hasMembership;

    private Map<Integer, Operation> paxosInstances;
    private List<Integer> instancesInUse;

    public StateMachine(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        nextInstance = 0;
        executedOps = 0;
        data = new HashMap<>();
        cumulativeHash = new byte[1];
        this.agreement = props.getProperty("agreement");
        this.paxosInstances = new HashMap<>();

        String address = props.getProperty("address");
        String port = props.getProperty("p2p_port");

        logger.info("Listening on {}:{}", address, port);
        this.self = new Host(InetAddress.getByName(address), Integer.parseInt(port));

        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, address);
        channelProps.setProperty(TCPChannel.PORT_KEY, port); //The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000");
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000");
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000");
        channelId = createChannel(TCPChannel.NAME, channelProps);

        /*-------------------- Register Message Serializers ----------------------- */
        registerMessageSerializer(channelId, RequestMembership.MSG_CODE, RequestMembership.serializer);
        registerMessageSerializer(channelId, InformMembership.MSG_CODE, InformMembership.serializer);

        /*-------------------- Register Message Handler ----------------------- */
        registerMessageHandler(channelId, RequestMembership.MSG_CODE, this::uponMembershipRequest, this::uponMsgFail);
        registerMessageHandler(channelId, InformMembership.MSG_CODE, this::uponInformMembership, this::uponMsgFail);

        /*-------------------- Register Reply Handler ----------------------- */
        registerReplyHandler(CurrentStateReply.REQUEST_ID, this::uponStateReply);

        /*-------------------- Register Channel Events ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(OrderRequest.REQUEST_ID, this::uponOrderRequest);

        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(DecidedNotification.NOTIFICATION_ID, this::uponDecidedNotification);
    }

    @Override
    public void init(Properties props) {
        //Inform the state machine protocol about the channel we created in the constructor
        triggerNotification(new ChannelReadyNotification(channelId, self));

        String host = props.getProperty("initial_membership");
        String[] hosts = host.split(",");
        List<Host> initialMembership = new LinkedList<>();
        for (String s : hosts) {
            String[] hostElements = s.split(":");
            Host h;
            try {
                h = new Host(InetAddress.getByName(hostElements[0]), Integer.parseInt(hostElements[1]));
            } catch (UnknownHostException e) {
                throw new AssertionError("Error parsing initial_membership", e);
            }
            initialMembership.add(h);
        }

        if (initialMembership.contains(self)) {
            state = State.ACTIVE;
            logger.info("Starting in ACTIVE as I am part of initial membership");
            //I'm part of the initial membership, so I'm assuming the system is bootstrapping
            membership = new LinkedList<>(initialMembership);
            membership.forEach(this::openConnection);
            triggerNotification(new JoinedNotification(membership, 0));
        } else {
            state = State.JOINING;
            logger.info("Starting in JOINING as I am not part of initial membership");

            membership = new LinkedList<>(initialMembership);
            membership.forEach(this::openConnection);
            sendMessage(new RequestMembership(), membership.get(0));
            sendRequest(new CurrentStateRequest(nextInstance - 1), PROTOCOL_ID);
            //TODO: Timer for membership request
        }

    }

    /*--------------------------------- Reply ---------------------------------------- */
    private void uponStateReply(CurrentStateReply reply, short sourceProto) {
        byte[] tempState = reply.getState();

        ByteArrayInputStream bais = new ByteArrayInputStream(tempState);
        DataInputStream dis = new DataInputStream(bais);
        try {
            this.executedOps = dis.readInt();
            this.cumulativeHash = new byte[dis.readInt()];
            dis.read(this.cumulativeHash);
            int mapSize = dis.readInt();
            for (int i = 0; i < mapSize; i++) {
                String key = dis.readUTF();
                byte[] value = new byte[dis.readInt()];
                dis.read(value);
                data.put(key, value);
            }

            this.hasState = true;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (this.hasMembership && this.hasState) {
            this.state = State.ACTIVE;

            triggerNotification(new JoinedNotification(membership, this.membership.size()));

            //TODO: WHEN JOINED RUN OPS
        }
    }


    /*--------------------------------- Requests ---------------------------------------- */
    private void uponOrderRequest(OrderRequest request, short sourceProto) {
        logger.debug("Received request: " + request);
        if (state == State.JOINING) {
            opsToBe.add(request.getOpId());

            try {
                operationMap.put(request.getOpId(), Operation.fromByteArray(request.getOperation()));
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

        } else if (state == State.ACTIVE) {
            //Also do something starter, we don't want an infinite number of instances active
            //Maybe you should modify what is it that you are proposing so that you remember that this
            //operation was issued by the application (and not an internal operation, check the uponDecidedNotification)

            while (instancesInUse.contains(nextInstance)) {
                if (nextInstance == MAX_INSTANCES)
                    nextInstance = 0;
                else
                    nextInstance++;
            }

            if (agreement.equalsIgnoreCase("paxos")) {
                sendRequest(new ProposeRequest(nextInstance, request.getOpId(), request.getOperation()),
                        Paxos.PROTOCOL_ID);
                instancesInUse.add(nextInstance);
                try {
                    paxosInstances.put(nextInstance++, Operation.fromByteArray(request.getOperation()));
                } catch (IOException e) {
                    e.printStackTrace();
                    //TODO: this and receive message from paxos and delete from list and paxosInstances after sending
                }
            } else {
                sendRequest(new ProposeRequest(nextInstance, request.getOpId(), request.getOperation()),
                        MultiPaxos.PROTOCOL_ID);
                instancesInUse.add(nextInstance);
                try {
                    paxosInstances.put(nextInstance++, Operation.fromByteArray(request.getOperation()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        logger.debug("Received notification: " + notification);
        //Maybe we should make sure operations are executed in order?
        //You should be careful and check if this operation if an application operation (and send it up)
        //or if this is an operations that was executed by the state machine itself (in which case you should execute)


        triggerNotification(new ExecuteNotification(notification.getOpId(), notification.getOperation()));
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    private void uponInformMembership(InformMembership msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received membership: " + msg);

        this.membership = msg.getMembership();

        membership.forEach(this::openConnection);

        this.membership.add(self);

        this.hasMembership = true;

        if (this.hasMembership && this.hasState) {
            this.state = State.ACTIVE;

            triggerNotification(new JoinedNotification(membership, this.membership.size()));

            //TODO: WHEN JOINED RUN OPS
        }
    }

    private void uponMembershipRequest(RequestMembership msg, Host host, short sourceProto, int channelId) {
        logger.debug("Membership requested by ", host);

        sendMessage(new InformMembership(membership), host);
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.info("Connection to {} is up", event.getNode());
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.debug("Connection to {} is down, cause {}", event.getNode(), event.getCause());
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        logger.debug("Connection to {} failed, cause: {}", event.getNode(), event.getCause());
        //Maybe we don't want to do this forever. At some point we assume he is no longer there.
        //Also, maybe wait a little bit before retrying, or else you'll be trying 1000s of times per second
        if (membership.contains(event.getNode()))
            openConnection(event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        //When new machine joins receives in connection
        //send membership
        logger.trace("Connection from {} is up", event.getNode());

        openConnection(event.getNode()); //FIXME: might be bugged and need channelId
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

}
