package protocols.broadcast.eagerpush;

import babel.core.GenericProtocol;
import babel.exceptions.HandlerRegistrationException;
import babel.generic.ProtoMessage;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.common.BroadcastRequest;
import protocols.broadcast.common.DeliverNotification;
import protocols.broadcast.eagerpush.messages.GossipMessage;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.common.notifications.NeighbourDown;
import protocols.membership.common.notifications.NeighbourUp;
import protocols.membership.cyclon.CyclonMembership;
import protocols.membership.partial.HyParView;

import java.io.IOException;
import java.util.*;

public class EagerPushBroadcast extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(EagerPushBroadcast.class);

    public static final String PROTOCOL_NAME = "EagerPush";
    public static final short PROTOCOL_ID = 200;
    private int t; //Fanout of the protocol
    private final Host myself; //My own address/port
    private Set<Host> neighbors; //My known neighbours (a.k.a peers the membership protocol told me about)
    private final Set<UUID> delivered; //Set of received messages (since we do not want to deliver the same msg twice)

    CyclonMembership cyclonMembership;
    HyParView hyParView;

    private int cId;

    //We can only start sending messages after the membership protocol informed us that the channel is ready
    private boolean channelReady;

    public EagerPushBroadcast(Properties properties, Host myself, GenericProtocol membership) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;
        neighbors = new HashSet<>();
        delivered = new HashSet<>();
        channelReady = false;

        //Checks what is the typ+e of the membership we are using so that we can obtain our node frieds from the membership algorithm
        if (membership instanceof CyclonMembership) {
            this.cyclonMembership = (CyclonMembership) membership;

        } else {
            this.hyParView = (HyParView) membership;
        }

        t = 1;

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcastRequest);


        /*--------------------- Register Notification Handlers ----------------------------- */
//        subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::uponNeighbourUp);
//        subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::uponNeighbourDown);
        subscribeNotification(ChannelCreated.NOTIFICATION_ID, this::uponChannelCreated);
//        subscribeNotification(NeighborsReply.NOTIFICATION_ID, this::neighborReply);


    }

//    private void neighborReply(NeighborsReply v, short i) {
//        neighbours = v.getNeighbours();
//        notify();
//    }

    private void uponChannelCreated(ChannelCreated notification, short sourceProto) {
        cId = notification.getChannelId();
        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);
        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(cId, GossipMessage.MSG_ID, GossipMessage.serializer);
        /*---------------------- Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, GossipMessage.MSG_ID, this::uponEagerMessage, this::uponMsgFail);
        } catch (HandlerRegistrationException e) {
            logger.error("Error registering message handler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        //Now we can start sending messages
        channelReady = true;
    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto,
                             Throwable throwable, int channelId) {

        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    private void uponEagerMessage(GossipMessage msg, Host from, short sourceProto, int channelId) {
        logger.info("Received {} from {}", msg, from);
        if (delivered.add(msg.getMid())) {
            //CHECKS MSG TTL FOR VALIDITY
            if (msg.getTtl() > -1) {

                triggerNotification(new DeliverNotification(msg.getMid(), msg.getSender(), msg.getContent()));

                if (cyclonMembership != null) {
                    neighbors = new HashSet<>(cyclonMembership.getNeighbours());
                } else {
                    neighbors = new HashSet<>(hyParView.getNeighbours());
                }

                t = (int) Math.ceil(Math.log(neighbors.size())) == 0 ? 1 : (int) Math.ceil(Math.log(neighbors.size()));

                GossipMessage msg2 = new GossipMessage(msg.getMid(), msg.getSender(), msg.getToDeliver(), msg.getContent(), msg.getTtl() - 1);

                if (!neighbors.isEmpty()) {
                    List<Host> unshuffledGossiptTargets = new LinkedList<>(neighbors);
                    Collections.shuffle(unshuffledGossiptTargets);
                    Set<Host> gossipTargets = new HashSet<>(unshuffledGossiptTargets.subList(0, t));
                    gossipTargets.forEach(host -> {

                        logger.info("Sent {} to {}", msg2, host);
                        openConnection(host);
                        sendMessage(msg2, host);

                    });

                }
            } else {
                logger.info("Message TTL expired");
            }
//
        }
    }

//    private void uponNeighbourDown(NeighbourDown notification, short sourceProto) {
//        for (Host h : notification.getNeighbours()) {
//            neighbors.remove(h);
//            logger.info("Neighbour down: " + h);
//        }
//        t = (int) Math.ceil(Math.log(neighbors.size()));
//
//    }
//
//    private void uponNeighbourUp(NeighbourUp notification, short sourceProto) {
//        for (Host h : notification.getNeighbours()) {
//            neighbors.add(h);
//            logger.info("New neighbour: " + h);
//        }
//        t = (int) Math.ceil(Math.log(neighbors.size()));
//    }

    private void uponBroadcastRequest(BroadcastRequest request, short sourceProto) {
        if (!channelReady) return;

        //Create the message object.
        GossipMessage msg = new GossipMessage(request.getMsgId(), request.getSender(), sourceProto, request.getMsg(), 20);

        //Call the same handler as when receiving a new GossipMessage (since the logic is the same)
        uponEagerMessage(msg, myself, getProtoId(), cId);

    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        //Nothing to do here, we just wait for event from the membership or the application and t can only change after overlay network changes

    }
}
