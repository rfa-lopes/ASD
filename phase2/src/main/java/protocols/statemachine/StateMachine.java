package protocols.statemachine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.MultiPaxos;
import protocols.agreement.Paxos;
import protocols.agreement.messages.InformLiderMessage;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.InformeLiderNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.ProposeRequest;
import protocols.app.messages.RequestMessage;
import protocols.app.requests.CurrentStateReply;
import protocols.app.requests.CurrentStateRequest;
import protocols.app.utils.Operation;
import protocols.statemachine.messages.DecidedMessage;
import protocols.statemachine.messages.InformMembership;
import protocols.statemachine.messages.RequestMembership;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.requests.OrderRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final int MAX_INSTANCES = 3;
    private Host paxosLider;

    private enum State {JOINING, ACTIVE}

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "StateMachine";
    public static final short PROTOCOL_ID = 200;

    private final Host self;     //My own address/port
    private final int channelId; //Id of the created channel

    private State state;
    private List<Host> membership;
    private int nextInstance;

    //State
    private int executedOps;
    private final Map<String, byte[]> data;
    private byte[] cumulativeHash;

    //Operation Buffer
    private UUID opExecuting;
    private Operation currentOp;
    private Queue<UUID> opsToBe;
    private Map<UUID, Operation> operationMap;

    //MISC
    private String agreement;
    private boolean hasState;
    private boolean hasMembership;

    //Paxos operations buffer --- provavelmente vai tudo de vela
    private Map<Integer, Operation> paxosInstances;
    private List<Integer> instancesInUse;
    private Map<UUID, Operation> operationsDecided;
    private Queue<UUID> operationQueue;


    public StateMachine(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        nextInstance = 0;
        executedOps = 0;
        data = new HashMap<>();
        cumulativeHash = new byte[1];
        this.agreement = props.getProperty("agreement");
        this.operationQueue = new ArrayDeque<>();
        opsToBe = new ArrayDeque<>();
        operationMap = new HashMap<>();
        opExecuting = null;
        currentOp = null;
        paxosLider = null;


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
        registerMessageSerializer(channelId, DecidedMessage.MSG_CODE, DecidedMessage.serializer);


        /*-------------------- Register Message Handler ----------------------- */
        registerMessageHandler(channelId, RequestMembership.MSG_CODE, this::uponMembershipRequest, this::uponMsgFail);
        registerMessageHandler(channelId, InformMembership.MSG_CODE, this::uponInformMembership, this::uponMsgFail);
        registerMessageHandler(channelId, DecidedMessage.MSG_CODE, this::uponDecidedMessage, this::uponMsgFail);


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
        subscribeNotification(InformeLiderNotification.NOTIFICATION_ID, this::uponLiderNotification);

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

        //TODO: REVER
        initialMembership.remove(0);

        if (initialMembership.contains(self)) {
            state = State.ACTIVE;
            logger.info("Starting in ACTIVE as I am part of initial membership");
            //I'm part of the initial membership, so I'm assuming the system is bootstrapping
            membership = new LinkedList<>(initialMembership);
            membership.forEach(this::openConnection);
            logger.info("Memb " + membership);
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

    //TODO: buffer requests if too many

    private void executeBufferedOps() throws IOException {
        logger.info("here==???????????????????????????????");
        while (instancesInUse.contains(nextInstance)) {
            if (nextInstance == MAX_INSTANCES)
                nextInstance = 0;
            else
                nextInstance++;
        }

        UUID opId = opsToBe.poll();
        Operation op = operationMap.get(opId);
        short agId;

        if (agreement.equalsIgnoreCase("paxos")) {
            agId = Paxos.PROTOCOL_ID;
        } else {
            agId = MultiPaxos.PROTOCOL_ID;
        }

        sendRequest(new ProposeRequest(nextInstance, opId, op.toByteArray()), agId);
        instancesInUse.add(nextInstance);
        operationQueue.add(opId);
        operationMap.remove(opId, op);
        paxosInstances.put(nextInstance++, op);
    }

    /*--------------------------------- Reply ---------------------------------------- */
    @SuppressWarnings("DuplicatedCode")
    private void uponStateReply(CurrentStateReply reply, short sourceProto) {
        byte[] tempState = reply.getState();
        logger.info("crash?2");

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
            logger.info("crash?");

            this.state = State.ACTIVE;

            triggerNotification(new JoinedNotification(membership, this.membership.size()));

            try {
                //try executing a buffered op if any exist
                if (!opsToBe.isEmpty()) {
                    //maybe tentar correr do buffer de operacoes
                    uponOrderRequest(new OrderRequest(opsToBe.peek(), operationMap.get(opsToBe.peek()).toByteArray()), PROTOCOL_ID);
                }
                //executeBufferedOps();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
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
            try {
                if (opExecuting == null) {
                    if (agreement.equalsIgnoreCase("paxos")) {
                        sendRequest(new ProposeRequest(nextInstance++, request.getOpId(), request.getOperation()),
                                Paxos.PROTOCOL_ID);
                        //instancesInUse.add(nextInstance);
                        //operationQueue.add(request.getOpId());
                        //paxosInstances.put(nextInstance++, Operation.fromByteArray(request.getOperation()));
                    } else {
                        sendRequest(new ProposeRequest(nextInstance++, request.getOpId(), request.getOperation()),
                                MultiPaxos.PROTOCOL_ID);
                    }
                    opExecuting = request.getOpId();
                    currentOp = Operation.fromByteArray(request.getOperation());

                    if (sourceProto == PROTOCOL_ID) {
                        opsToBe.remove();
                        operationMap.remove(opExecuting, currentOp);
                    }
                } else if(sourceProto != PROTOCOL_ID){
                    //put operation in buffer
                    opsToBe.add(request.getOpId());
                    operationMap.put(request.getOpId(), Operation.fromByteArray(request.getOperation()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }


    /*--------------------------------- Notifications ---------------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        logger.debug("Received notification: " + notification);

        try {
            triggerNotification(new ExecuteNotification(notification.getOpId(), notification.getOperation()));

            Operation op = Operation.fromByteArray(notification.getOperation());

            //State update
            this.executedOps++;
            this.cumulativeHash = appendOpToHash(this.cumulativeHash, op.getData());
            if (op.getOpType() == RequestMessage.WRITE)
                this.data.put(op.getKey(), op.getData());

            UUID decided = notification.getOpId();

            //operationsDecided.put(decided, op);

            if(opExecuting == null)
                return;

            if (opExecuting.equals(decided)) {
                opExecuting = null;
                currentOp = null;

                if (!opsToBe.isEmpty()) {
                    //maybe tentar correr do buffer de operacoes
                    uponOrderRequest(new OrderRequest(opsToBe.peek(), operationMap.get(opsToBe.peek()).toByteArray()), PROTOCOL_ID);
                }
            } else {
                //Try again
                uponOrderRequest(new OrderRequest(opExecuting, currentOp.toByteArray()), PROTOCOL_ID);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

/*
        if (operationQueue.peek().equals(notification.getOpId())) {
            triggerNotification(new ExecuteNotification(notification.getOpId(), notification.getOperation()));
            byte[] state = null;
            try {
                state = getCurrentState();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //for (Host h : membership)
            //  sendMessage(new DecidedMessage(state, notification.getInstance(), notification.getOpId(), notification.getOperation()), h);
            operationQueue.remove(notification.getOpId());
            try {
                operationsDecided.remove(notification.getOpId(), Operation.fromByteArray(notification.getOperation()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                //TODO:Tratar deste buffer de ops
                operationsDecided.put(notification.getOpId(), Operation.fromByteArray(notification.getOperation()));
                Operation op = null;
                try {
                    op = Operation.fromByteArray(notification.getOperation());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                this.cumulativeHash = appendOpToHash(this.cumulativeHash, op.getData());
                if (op.getOpType() == RequestMessage.WRITE)
                    this.data.put(op.getKey(), op.getData());
                this.executedOps++;
            } catch (IOException e) {
                e.printStackTrace();
            }
    }*/
    }

    private void uponLiderNotification(InformeLiderNotification informeLiderNotification, short sourceProto) {
        paxosLider = informeLiderNotification.getLider();
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    @SuppressWarnings("DuplicatedCode")
    private void uponInformMembership(InformMembership msg, Host host, short sourceProto, int channelId) {
        logger.info("Received membership: " + msg);

        this.membership = msg.getMembership();

        membership.forEach(this::openConnection);

        this.membership.add(self);

        this.hasMembership = true;

        if (this.hasMembership && this.hasState) {
            this.state = State.ACTIVE;

            triggerNotification(new JoinedNotification(membership, this.membership.size()));

            try {
                //try executing a buffered op if any exist
                if (!opsToBe.isEmpty()) {
                    //maybe tentar correr do buffer de operacoes
                    uponOrderRequest(new OrderRequest(opsToBe.peek(), operationMap.get(opsToBe.peek()).toByteArray()), PROTOCOL_ID);
                }
                //executeBufferedOps();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void uponDecidedMessage(DecidedMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Decided Message received from ", host);

        Operation op = null;
        try {
            op = Operation.fromByteArray(msg.getOperation());
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(msg.getState());
        DataInputStream dis = new DataInputStream(bais);
        int executedOps = 0;
        byte[] cumulativeHash = null;
        Map<String, byte[]> data = null;

        try {
            executedOps = dis.readInt();
            cumulativeHash = new byte[dis.readInt()];
            data = new HashMap<>();
            dis.read(cumulativeHash);
            int mapSize = dis.readInt();
            for (int i = 0; i < mapSize; i++) {
                String key = dis.readUTF();
                byte[] value = new byte[dis.readInt()];
                dis.read(value);
                data.put(key, value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        execute(op, cumulativeHash, executedOps, data);
    }

    private void uponMembershipRequest(RequestMembership msg, Host host, short sourceProto, int channelId) {
        logger.info("Membership requested by ", host);

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

        openConnection(event.getNode(), channelId); //FIXME: might be bugged and need channelId
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    private byte[] appendOpToHash(byte[] hash, byte[] op) {
        MessageDigest mDigest;
        try {
            mDigest = MessageDigest.getInstance("sha-256");
        } catch (NoSuchAlgorithmException e) {
            logger.error("sha-256 not available...");
            throw new AssertionError("sha-256 not available...");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(hash);
            baos.write(op);
            return mDigest.digest(baos.toByteArray());
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AssertionError();
        }
    }

    private void execute(Operation op, byte[] cumulativeHash, int executedOps, Map<String, byte[]> data) {
        if (!Arrays.equals(this.cumulativeHash, cumulativeHash)) {
            this.cumulativeHash = cumulativeHash;
            this.data.clear();
            this.data.putAll(data);
            this.executedOps = executedOps;
        }

        this.cumulativeHash = appendOpToHash(this.cumulativeHash, op.getData());

        if (op.getOpType() == RequestMessage.WRITE)
            this.data.put(op.getKey(), op.getData());

        this.executedOps++;
    }

    private byte[] getCurrentState() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(executedOps);
        dos.writeInt(cumulativeHash.length);
        dos.write(cumulativeHash);
        dos.writeInt(data.size());
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeInt(entry.getValue().length);
            dos.write(entry.getValue());
        }
        return baos.toByteArray();
    }

}
