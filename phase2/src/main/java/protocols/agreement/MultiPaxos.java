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
public class MultiPaxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(MultiPaxos.class);

    //Protocol information, to register in babel
    public final static short PROTOCOL_ID = 9021;
    public final static String PROTOCOL_NAME = "MultiPaxos";

    @SuppressWarnings("FieldCanBeLocal")
    private Host myself;
    private Host lider;
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


    public MultiPaxos(Properties properties) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        joinedInstance = -1; //-1 means we have not yet joined the system
        membership = null;
        lider = null;

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

        /* Register Timer Handlers ----------------------------- */
        registerTimerHandler(PrepareTimer.TIMER_ID, this::uponResetTimer);
        registerTimerHandler(AcceptTimer.TIMER_ID, this::uponResetTimer);

        /* Register Request Handlers --------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(AddReplicaRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(RemoveReplicaRequest.REQUEST_ID, this::uponRemoveReplica);

        /* Register Notification Handlers ---------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);

        //create timer for lider sending NO_OP Messages to replicas

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

        //TODO: REGISTAR AS MINHAS MSGS

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
        logger.info("Agreement starting at instance {},  membership: {}", joinedInstance, membership);
    }

    /*----------------------------------------REQUESTS------------------------------------------------*/

    //Proposer
    private void uponProposeRequest(ProposeRequest request, short sourceProto) {
        /* A proposer receives a consensus request for a VALUE from a client.
        It creates a unique proposal number, ID, and sends a PREPARE(ID)
        message to at least a majority of acceptors.*/

        //we gotta check who is the leader
        //In case we do not have a leader, we start a prepare to find it
        if(lider == null){

            logger.debug("Received " + request);
            sequenceNumber= request.getInstance();
            opId = request.getOpId();
            operation = request.getOperation();

            logger.debug("Sending to: " + membership);
            PrepareMessage prepareMessage = new PrepareMessage(sequenceNumber);
            membership.forEach(h -> sendMessage(prepareMessage, h));

            prepareTimer = setupPeriodicTimer(new PrepareTimer(), PREPARE_TIME, PREPARE_TIME);
        }else{

            // in case we already have one
            // we still have to verify two things : If myself is a lider, then proceed sending accept to all replicas
            //Else, we have to contact lider replica by forwarding the request
            if(myself == lider){

                //proceed to sending accepts to replicas
                AcceptMessage acceptMessage = new AcceptMessage(sequenceNumber, opId, operation);
                logger.debug("Sending to: {}, AcceptMessage: {}", membership, acceptMessage);
                membership.forEach(h -> sendMessage(acceptMessage, h));
                //setup timer

            }else{

                //forwards the request to lider since we are a comun replica
               ForwardRequestMessage forwardRequestMessage = new ForwardRequestMessage(request.getInstance(), request.getOpId(), request.getOperation());
               //TODO : Should we setup time now? or replicas should just send no matter what, and keep verifying liveness?
                logger.debug("Sending to: {}, forwardRequestMessage: {}", membership, forwardRequestMessage);
                sendMessage(forwardRequestMessage, lider);

            }
        }


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

    //Accepter
    private void uponPrepareMessage(PrepareMessage msg, Host host, short sourceProto, int channelId) {

        logger.debug("Received " + msg);
        int receivedSequenceNumber = msg.getSequenceNumber();

        //if n > np then
        if( receivedSequenceNumber > sequenceNumber ){

            //np = n // will not accept anything < n
            sequenceNumber = receivedSequenceNumber;

            if(lider == null){

                //reply <PREPARE_OK,na,va> in case we have no leader
                PrepareOkMessage prepareMessage = new PrepareOkMessage(sequenceNumber);
                logger.debug("Sending to: {}, prepareMessage: {}", host, prepareMessage);
                sendMessage(prepareMessage, host);

                //then we found our lider
                lider = host;

            }else{
                //if we already have a lider we have to reply with an information message indicating who is lider
                InformLiderMessage informLiderMessage = new InformLiderMessage(lider);
                logger.debug("Sending to: {}, InformLiderMessage: {}", host, informLiderMessage);
                sendMessage(informLiderMessage, host);
            }


        }
    }

    //Proposer
    private void uponPrepareOkMessage(PrepareOkMessage msg, Host host, short sourceProto, int channelId) {
        logger.debug("Received " + msg);

        if( ++prepareOkMessagesReceived == getQuorumSize()){
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

    //Proposer / Learner
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
            acceptedMessages = 1;

        }else {
            if (receivedSequenceNumber < sequenceNumber)
                return;
        }

        //aset.add(a)
        acceptedMessages++;

        //if aset is a (majority) quorum
        if(acceptedMessages == getQuorumSize()) {
            //  decision = va
            cancelTimer(acceptTimer);

            //send DECIDED(va) to client
            DecidedNotification decidedNotification = new DecidedNotification(sequenceNumber, opId, operation);
            logger.debug("Trigger DecidedNotification: " + decidedNotification);
            triggerNotification(decidedNotification);
        }
    }

    private void uponForwardingMessage(ForwardRequestMessage msg, Host host, short sourceProto, int channelId){
        //I am a lider and received a forwardMessage, i should now process it and in case of accepting it, distribute it to all other replicas

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

            //Send the accepts to all replicas

            AcceptMessage acceptMessage = new AcceptMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, AcceptMessage: {}", membership, acceptMessage);
            membership.forEach(h -> sendMessage(acceptMessage, h));

            //send <ACCEPT_OK,na,va> to all learners
            AcceptOkMessage acceptOkMessageToLearners = new AcceptOkMessage(sequenceNumber, opId, operation);
            logger.debug("Sending to: {}, prepareMessage: {}", membership, acceptOkMessageToLearners);
            membership.forEach(h -> sendMessage(acceptOkMessageToLearners, h));
        }


    }

    private void uponInformLiderMessage(InformLiderMessage msg, Host host, short sourceProto, int channelId){

        logger.debug("Received " + msg);

        //the process updates the informed lider
        lider = new Host(msg.getHost().getAddress(), msg.getHost().getPort());

        //TODO :should we now inform lider that we still have a client request?

        ForwardRequestMessage forwardRequestMessage = new ForwardRequestMessage(sequenceNumber, opId, operation);
        logger.debug("Sending to: {}, forwardRequestMessage: {}", membership, forwardRequestMessage);
        sendMessage(forwardRequestMessage, lider);


    }


    private void uponHeartBeat(LiderHeartBeatMessage msg, Host host, short sourceProto, int channelId){

        logger.debug("Received " + msg);
        //FIXME -> MONITORIZATION OF LIDER

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

    private int getNextSequenceNumber() {
        return this.sequenceNumber + membership.size();
    }

    private int getQuorumSize(){
        return (membership.size() / 2) + 1;
    }
}
