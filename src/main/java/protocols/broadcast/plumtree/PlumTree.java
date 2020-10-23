package protocols.broadcast.plumtree;

import babel.core.GenericProtocol;
import babel.exceptions.HandlerRegistrationException;
import babel.generic.ProtoRequest;
import network.data.Host;
import protocols.broadcast.common.BroadcastRequest;
import protocols.broadcast.common.DeliverNotification;
import protocols.broadcast.eagerpush.messages.GossipMessage;
import protocols.broadcast.plumtree.messages.LazyMessage;
import protocols.broadcast.plumtree.messages.PlumMessage;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.common.notifications.NeighbourDown;
import protocols.membership.common.notifications.NeighbourUp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class PlumTree extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(PlumTree.class);

    public static final String PROTOCOL_NAME = "PlumTree";
    public static final short PROTOCOL_ID = 690;

    private int t; //Fanout of the protocol
    private final Host myself; //My own address/port
    private final Set<UUID> delivered; //Set of received messages (since we do not want to deliver the same msg twice)

    private final Set<Host> eagerPushPeers;
    private final Set<Host> lazyPushPeers;
    private final Queue<LazyMessage> lazyQueue;
    private final Set<UUID> missing;
    private final Set<UUID> received;


    //We can only start sending messages after the membership protocol informed us that the channel is ready
    private boolean channelReady;

    public PlumTree(Properties properties, Host myself) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;
        this.delivered = new HashSet<>();

        this.eagerPushPeers = new HashSet<>();
        this.lazyPushPeers = new HashSet<>();
        this.lazyQueue = new ArrayDeque<>();
        this.missing = new HashSet<>();
        this.received = new HashSet<>();

        this.channelReady = false;

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcastRequest);

        /*--------------------- Register Notification Handlers ----------------------------- */
        //subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::uponNeighbourUp);
        //subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::uponNeighbourDown);
        //subscribeNotification(ChannelCreated.NOTIFICATION_ID, this::uponChannelCreated);
    }

    private void uponBroadcastRequest(BroadcastRequest request, short sourceProto) {
        if (!channelReady) return;

        GossipMessage message = new GossipMessage(request.getMsgId(), request.getSender(), sourceProto, request.getMsg());

        eagerPush(message, getProtoId());

        lazyPush(request, getProtoId());

        triggerNotification(new DeliverNotification(message.getMid(), message.getSender(), message.getContent()));

        received.add(request.getMsgId());

    }

    private void eagerPush(GossipMessage message, short sourceProto){

        for (Host h : eagerPushPeers) {
            if (h != myself) {
                sendMessage(message, h); //rever
            }
        }
    }

    private void lazyPush(BroadcastRequest request, short sourceProto){

        for (Host h : lazyPushPeers) {
            if (h != myself) {
                lazyQueue.add(new LazyMessage(request.getMsgId(), request.getSender(), sourceProto));
            }
        }

        dispatch();

    }

    private void dispatch(){
        //TODO
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        //Nothing to do here, we just wait for event from the membership or the application and t can only change after overlay network changes

    }

    //TODO: DISPATCH, CHANNEL CREATED, RECEIVE BOTH OF THEM AND INIT

}
