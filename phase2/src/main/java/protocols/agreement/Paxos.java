package protocols.agreement;

import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.agreement.timers.AcceptTimer;
import protocols.agreement.timers.PrepareTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.agreement.requests.ProposeRequest;
import java.util.*;

/*Made by Rodrigo*/
public class Paxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public final static short PROTOCOL_ID = 100;
    public final static String PROTOCOL_NAME = "Paxos";

    @SuppressWarnings("FieldCanBeLocal")
    private Host myself;
    private int joinedInstance;
    private List<Host> membership;

    private int prepareOkMessagesReceived;
    private int acceptedMessages;

    private int sequenceNumber;
    private UUID opId;
    private byte[] operation;

    private final int PREPARE_TIME;
    private long prepareTimer;

    private final int ACCEPT_TIME;
    private long acceptTimer;

    public Paxos(Properties properties) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        joinedInstance = -1; //-1 means we have not yet joined the system
        membership = null;

        sequenceNumber = 0;
        operation = null;
        opId = null;

        prepareOkMessagesReceived = 0;
        acceptedMessages = 0;

        prepareTimer = -1;
        acceptTimer = -1;

        //Get some configurations from the Properties object
        PREPARE_TIME =  Integer.parseInt(properties.getProperty("prepareTime", "3000"));
        ACCEPT_TIME =  Integer.parseInt(properties.getProperty("acceptTime", "3000"));

        // Register Timer Handlers
        registerTimerHandler(PrepareTimer.TIMER_ID, this::uponResetTimer);
        registerTimerHandler(AcceptTimer.TIMER_ID, this::uponResetTimer);

        // Register Request Handlers
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(AddReplicaRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(RemoveReplicaRequest.REQUEST_ID, this::uponRemoveReplica);

        // Register Notification Handlers
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);
    }


    @Override
    public void init(Properties props) {
        //Nothing to do here, we just wait for events from the application or agreement
    }

    /*----------------------------------------NOTIFICATIONS------------------------------------------------*/

    //Upon receiving the channelId from the membership, register our own callbacks and serializers
    @SuppressWarnings("DuplicatedCode")
    private void uponChannelCreated(ChannelReadyNotification notification, short sourceProto) {
        int cId = notification.getChannelId();
        myself = notification.getMyself();

        logger.info("Channel {} created, I am {}", cId, myself);

        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);

        // Register Message Serializers
        registerMessageSerializer(cId, PrepareMessage.MSG_CODE, PrepareMessage.serializer);
        registerMessageSerializer(cId, PrepareOkMessage.MSG_CODE, PrepareOkMessage.serializer);
        registerMessageSerializer(cId, AcceptMessage.MSG_CODE, AcceptMessage.serializer);
        registerMessageSerializer(cId, AcceptOkMessage.MSG_CODE, AcceptOkMessage.serializer);

        // Register Message Handlers
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
        logger.info("Join Notification: " + notification);
        joinedInstance = notification.getJoinInstance();
        membership = new LinkedList<>(notification.getMembership());
        membership.forEach(this::openConnection); // TODO: Tenho que fazer isto?
        logger.info("Agreement starting at instance {},  membership: {}", joinedInstance, membership);
    }

    /*----------------------------------------REQUESTS------------------------------------------------*/

    //Proposer
    @SuppressWarnings("DuplicatedCode")
    private void uponProposeRequest(ProposeRequest request, short sourceProto) {
        logger.debug("Received " + request);

        sequenceNumber= request.getInstance();
        opId = request.getOpId();
        operation = request.getOperation();

        logger.debug("Sending to: " + membership);
        PrepareMessage prepareMessage = new PrepareMessage(sequenceNumber);
        membership.forEach(h -> sendMessage(prepareMessage, h));

        prepareTimer = setupPeriodicTimer(new PrepareTimer(), PREPARE_TIME, PREPARE_TIME);
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        logger.info("Received " + request);
        //The AddReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.add(request.getReplica());
        openConnection(request.getReplica());// TODO: Tenho que fazer isto?
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        logger.debug("Received " + request);
        //The RemoveReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.remove(request.getReplica());
        closeConnection(request.getReplica());// TODO: Tenho que fazer isto?
    }

    /*----------------------------------------MESSAGES HANDLERS------------------------------------------------*/

    //Accepter
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

    //Proposer
    private void uponPrepareOkMessage(PrepareOkMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);

        if(msg.getSequenceNumber() != sequenceNumber)
            return;

        prepareOkMessagesReceived++;

        if( prepareOkMessagesReceived == getQuorumSize() ){
            cancelTimer(prepareTimer);

            AcceptMessage acceptMessage = new AcceptMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, prepareMessage: {}", membership, acceptMessage);
            membership.forEach(h -> sendMessage(acceptMessage, h));

            acceptTimer = setupPeriodicTimer(new AcceptTimer(), ACCEPT_TIME, ACCEPT_TIME);
        }
    }

    //Accepter
    private void uponAcceptMessage(AcceptMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);

        int receivedSequenceNumber = msg.getSequenceNumber();

        if( receivedSequenceNumber >= sequenceNumber ){
            sequenceNumber = receivedSequenceNumber;
            opId = msg.getOpId();
            operation = msg.getOperation();

            AcceptOkMessage acceptOkMessageToLearners = new AcceptOkMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, prepareMessage: {}", membership, acceptOkMessageToLearners);
            membership.forEach(h -> sendMessage(acceptOkMessageToLearners, h));
        }
    }

    //Learner (Regular approach)
    private void uponAcceptOkMessage(AcceptOkMessage msg, Host host, short sourceProto, int channelId) {

        //Ha duas alternativas chave:
        // distinguished proposer, em que o proposer quando recebe a maioria de AcceptOK manda decided para todos os learners,
        // ou o regular em que os acceptors mandam os accept ok para todas as replicas,
        // e quando um learner observa uma maioria de accepts iguais, decretam a decisão.
        //Estamos a usar o regular.

        logger.debug("Received " + msg);

        int receivedSequenceNumber = msg.getSequenceNumber();

        if(receivedSequenceNumber > sequenceNumber){
            sequenceNumber = receivedSequenceNumber;
            opId = msg.getOpId();
            operation = msg.getOperation();
            acceptedMessages = 1;
        }else if (receivedSequenceNumber < sequenceNumber)
            return;

        if( ++acceptedMessages == getQuorumSize()) {
            cancelTimer(acceptTimer);

            DecidedNotification decidedNotification = new DecidedNotification(sequenceNumber, opId, operation);
            logger.debug("Trigger DecidedNotification: " + decidedNotification);
            triggerNotification(decidedNotification);
        }
    }

    /*----------------------------------------TIMERS------------------------------------------------*/

    private void uponResetTimer(PrepareTimer v, long timerId) {
        //Reset algorithm using a larger sequence number (n)
        logger.debug("Reset Timer.");
        sequenceNumber = getNextSequenceNumber();
        acceptedMessages = 0;
        prepareOkMessagesReceived = 0;
        uponProposeRequest(new ProposeRequest(sequenceNumber, opId, operation), PROTOCOL_ID);
    }

    /*----------------------------------------FAILS------------------------------------------------*/

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /*----------------------------------------AUXILIARIES------------------------------------------------*/

    //For the case when maxSeqNumber==100 and nextSeqNumberOfInstance==3 -> nextSeqNumberOfInstance==103
    private int getNextSequenceNumber() {
        int nextSequenceNumber = this.joinedInstance + membership.size();
        //Maybe exists a more mathematical and efficient way to do this, but for now this works pretty fine :)
        while(nextSequenceNumber <= sequenceNumber)
            nextSequenceNumber += membership.size();
        return nextSequenceNumber;
    }

    private int getQuorumSize(){
        return (membership.size() / 2) + 1;
    }
}
