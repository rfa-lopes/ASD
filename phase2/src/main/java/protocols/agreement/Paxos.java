package protocols.agreement;

import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.agreement.timers.PrepareTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.agreement.requests.ProposeRequest;

import java.io.IOException;
import java.util.*;

/*Made by Rodrigo*/
@SuppressWarnings("ALL")
public class Paxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(MultiPaxos.class);

    //Protocol information, to register in babel
    public final static short PROTOCOL_ID = 9020;
    public final static String PROTOCOL_NAME = "Paxos";

    private Host myself;
    private int joinedInstance;
    private List<Host> membership;
    private List<Host> quorumMembership;
    private Map<Host, PrepareOkMessage> prepareOkMessagesReceived;
    private Map<Host, AcceptOkMessage> acceptedMessages;
    private final Random rnd;

    private int sequenceNumber;
    private UUID opId;
    private byte[] operation;

    private final int PREPARE_TIME;
    private long prepareTimer;

    public Paxos(Properties properties) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        joinedInstance = -1; //-1 means we have not yet joined the system
        membership = null;
        quorumMembership = null;
        sequenceNumber = 0;
        operation = null;
        opId = null;
        prepareOkMessagesReceived = null;
        acceptedMessages = null;
        rnd = new Random();
        prepareTimer = -1;

        //Get some configurations from the Properties object
        PREPARE_TIME =  Integer.parseInt(properties.getProperty("prepareTime", "3000"));

        /* Register Timer Handlers ----------------------------- */
        registerTimerHandler(PrepareTimer.TIMER_ID, this::uponPrepareTimer);

        /* Register Request Handlers --------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(AddReplicaRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(RemoveReplicaRequest.REQUEST_ID, this::uponRemoveReplica);

        /* Register Notification Handlers ---------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);
    }


    @Override
    public void init(Properties props) {
        //Nothing to do here, we just wait for events from the application or agreement
    }

    /*----------------------------------------NOTIFICATIONS------------------------------------------------*/

    //Upon receiving the channelId from the membership, register our own callbacks and serializers
    private void uponChannelCreated(ChannelReadyNotification notification, short sourceProto) {
        int cId = notification.getChannelId();
        myself = notification.getMyself();

        logger.info("Channel {} created, I am {}", cId, myself);

        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);

        /* Register Message Serializers ---------------------- */
        registerMessageSerializer(cId, PrepareMessage.MSG_CODE, PrepareMessage.serializer);
        registerMessageSerializer(cId, PrepareOkMessage.MSG_CODE, PrepareOkMessage.serializer);
        registerMessageSerializer(cId, AcceptMessage.MSG_CODE, AcceptMessage.serializer);
        registerMessageSerializer(cId, AcceptOkMessage.MSG_CODE, AcceptOkMessage.serializer);

        /* Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, PrepareMessage.MSG_CODE, this::uponPrepareMessage, this::uponMsgFail);
            registerMessageHandler(cId, PrepareOkMessage.MSG_CODE, this::uponPrepareOkMessage, this::uponMsgFail);
            registerMessageHandler(cId, AcceptMessage.MSG_CODE, this::uponAcceptMessage, this::uponMsgFail);
            registerMessageHandler(cId, AcceptOkMessage.MSG_CODE, this::uponAcceptOkMessage, this::uponMsgFail);
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }
    }

    private void uponJoinedNotification(JoinedNotification notification, short sourceProto) {
        //We joined the system and can now start doing things
        joinedInstance = notification.getJoinInstance();
        membership = new LinkedList<>(notification.getMembership());
        prepareOkMessagesReceived = new HashMap<>(membership.size());
        logger.info("Agreement starting at instance {},  membership: {}", joinedInstance, membership);
    }

    /*----------------------------------------REQUESTS------------------------------------------------*/

    private void uponProposeRequest(ProposeRequest request, short sourceProto) {
        /* A proposer receives a consensus request for a VALUE from a client.
        It creates a unique proposal number, ID, and sends a PREPARE(ID)
        message to at least a majority of acceptors.*/

        logger.debug("Received " + request);

        sequenceNumber= request.getInstance();
        opId = request.getOpId();
        operation = request.getOperation();
        PrepareMessage prepareMessage = new PrepareMessage(sequenceNumber);

        logger.debug("Sending to: " + membership);

        membership.forEach(h -> sendMessage(prepareMessage, h));
        prepareTimer = setupPeriodicTimer(new PrepareTimer(), PREPARE_TIME, PREPARE_TIME);
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        logger.debug("Received " + request);
        //The AddReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.add(request.getReplica());
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        logger.debug("Received " + request);
        //The RemoveReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.remove(request.getReplica());
    }

    /*----------------------------------------MESSAGES HANDLERS------------------------------------------------*/

    private void uponPrepareMessage(PrepareMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);
        int receivedSequenceNumber = msg.getSequenceNumber();
        if( receivedSequenceNumber > sequenceNumber ){
            sequenceNumber = receivedSequenceNumber;
            PrepareOkMessage prepareMessage = new PrepareOkMessage(sequenceNumber);
            logger.debug("Sending to: {}, prepareMessage: {}", host, prepareMessage);
            sendMessage(prepareMessage, host);
        }
    }

    private void uponPrepareOkMessage(PrepareOkMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);
        prepareOkMessagesReceived.put(host, msg);
        if(prepareOkMessagesReceived.size() == getQuorumSize()){
            cancelTimer(prepareTimer);
            AcceptMessage acceptMessage = new AcceptMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, prepareMessage: {}", membership, acceptMessage);
            membership.forEach(h -> sendMessage(acceptMessage, h));
        }
    }

    private void uponAcceptMessage(AcceptMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);
        int receivedSequenceNumber = msg.getSequenceNumber();
        UUID receivedOpId = msg.getOpId();
        byte[] receivedOperation = msg.getOperation();

        //if n >= np then
        if( receivedSequenceNumber >= sequenceNumber ){

            //na = n
            //va = v
            sequenceNumber = receivedSequenceNumber;
            opId = receivedOpId;
            operation = receivedOperation;

            //reply with <ACCEPT_OK,n>
            AcceptOkMessage acceptOkMessage = new AcceptOkMessage(sequenceNumber);
            logger.debug("Sending to: {}, prepareMessage: {}", host, acceptOkMessage);
            sendMessage(acceptOkMessage, host);

            //send <ACCEPT_OK,na,va> to all learners
            AcceptOkMessage acceptOkMessageToLearners = new AcceptOkMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, prepareMessage: {}", membership, acceptOkMessageToLearners);
            membership.forEach(h -> sendMessage(acceptOkMessageToLearners, h));
        }
    }

    private void uponAcceptOkMessage(AcceptOkMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);

        int receivedSequenceNumber = msg.getSequenceNumber();
        UUID receivedOpId = msg.getOpId();
        byte[] receivedOperation = msg.getOperation();

        if(receivedSequenceNumber > sequenceNumber){

            //na = n
            //va = v
            sequenceNumber = receivedSequenceNumber;
            opId = receivedOpId;
            operation = receivedOperation;
            acceptedMessages.clear();

        }else if(receivedSequenceNumber < sequenceNumber)
            return;

        //aset.add(a)
        acceptedMessages.put(host, msg);

        //if aset is a (majority) quorum
        //  decision = va
        if(haveMajorityQuorum()){
            DecidedNotification decidedNotification = new DecidedNotification(sequenceNumber, opId, operation);
            triggerNotification(decidedNotification);
            logger.debug("Trigger DecidedNotification: " + decidedNotification);
        }

    }

    /*----------------------------------------TIMERS------------------------------------------------*/

    private void uponPrepareTimer(PrepareTimer v, long timerId) {
        //TODO: uponPrepareTimer -> Reset algorithm using a larger sequence number (n)
        sequenceNumber = getNextSequenceNumber();
        acceptedMessages.clear();
        prepareOkMessagesReceived.clear();

        ProposeRequest proposeRequest = new ProposeRequest(sequenceNumber, opId, operation);
        uponProposeRequest(proposeRequest, PROTOCOL_ID);
    }

    /*----------------------------------------FAILS------------------------------------------------*/

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /*----------------------------------------AUXILIARIES------------------------------------------------*/

    private int getNextSequenceNumber() {
        return this.sequenceNumber + membership.size();
    }

    private int getQuorumSize(){
        return (membership.size() / 2) + 1;
    }

    private boolean haveMajorityQuorum() {
        if(acceptedMessages.size() <= 2)
            return membership.size() == acceptedMessages.size();
        return acceptedMessages.size() >= (membership.size() / 2) + 1;
    }

}
