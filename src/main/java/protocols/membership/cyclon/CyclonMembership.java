package protocols.membership.cyclon;

import babel.core.GenericProtocol;
import babel.exceptions.HandlerRegistrationException;
import babel.generic.ProtoMessage;
import channel.tcp.TCPChannel;
import channel.tcp.events.*;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.membership.common.notifications.ChannelCreated;
import protocols.membership.cyclon.messages.ShuffleReply;
import protocols.membership.cyclon.messages.ShuffleRequest;
import protocols.membership.cyclon.utils.HostWithTime;
import protocols.membership.full.timers.InfoTimer;
import protocols.membership.full.timers.SampleTimer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CyclonMembership extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(CyclonMembership.class);

    public final static short PROTOCOL_ID = 100;
    public final static String PROTOCOL_NAME = "CyclonMembership";

    private final Host self;     //My own address/port

    private final Map<Host, HostWithTime> neigh; //Peers I am connected to
    private final Map<Host, Integer> attempts;

    private final int sampleTime; //param: timeout for samples
    private final int subsetSize; //param: maximum size of sample;

    private final int channelId; //Id of the created channel
    private Set<HostWithTime> sample;

    private final Random rnd;


    public CyclonMembership(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.self = self;
        this.neigh = new ConcurrentHashMap<>();
        this.sample = new HashSet<>();

        this.rnd = new Random();

        //Get some configurations from the Properties object
        this.sampleTime = Integer.parseInt(props.getProperty("sample_time", "2000")); //2 seconds
        this.subsetSize = Integer.parseInt(props.getProperty("sample_size"));

        String cMetricsInterval = props.getProperty("channel_metrics_interval", "10000"); //10 seconds

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, cMetricsInterval); //The interval to receive channel metrics
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout
        channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, ShuffleReply.MSG_ID, ShuffleReply.serializer);
        registerMessageSerializer(channelId, ShuffleRequest.MSG_ID, ShuffleRequest.serializer);


        /*---------------------- Register Message Handlers -------------------------- */
        registerMessageHandler(channelId, ShuffleRequest.MSG_ID, this::uponShuffleRequest, this::uponMsgFail);
        registerMessageHandler(channelId, ShuffleReply.MSG_ID, this::uponShuffleReply);


        /*--------------------- Register Timer Handlers ----------------------------- */
        registerTimerHandler(SampleTimer.TIMER_ID, this::uponShuffle);
        registerTimerHandler(InfoTimer.TIMER_ID, this::uponInfoTime);

        /*-------------------- Register Channel Events ------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, ChannelMetrics.EVENT_ID, this::uponChannelMetrics);

        attempts = new ConcurrentHashMap<>();
    }


    @Override
    public void init(Properties props) {

        //Inform the dissemination protocol about the channel we created in the constructor
        triggerNotification(new ChannelCreated(channelId));


        //If there is a contact node, attempt to establish connection
        if (props.containsKey("contact")) {
            try {

                String contact = props.getProperty("contact");
                String[] hostElems = contact.split(":");
                Host contactHost = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));

                HostWithTime hos = new HostWithTime();
                hos.setHost(contactHost);
                hos.setAge(0);
                neigh.put(contactHost, hos);

            } catch (Exception e) {

                logger.error("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }

        //Setup the timer used to shuffle
        setupPeriodicTimer(new SampleTimer(), this.sampleTime, this.sampleTime);

        //Setup the timer to display protocol information (also registered handler previously)
        int pMetricsInterval = Integer.parseInt(props.getProperty("protocol_metrics_interval", "10000"));

        if (pMetricsInterval > 0)
            setupPeriodicTimer(new InfoTimer(), pMetricsInterval, pMetricsInterval);
    }

    private void uponShuffle(SampleTimer timer, long timerId) {

        if (!neigh.isEmpty()) {

            //When the triggered increments all ages of neigh and gets the oldest to check if it is alive
            logger.debug("Shuffle: membership{}", neigh.keySet());

            neigh.values().forEach(host -> host.setAge(host.getAge() + 1));
            HostWithTime hostWithTime = getOldest(neigh);

            //removes oldest from neigh
            neigh.values().forEach(host -> {
                if (host.getHost().equals(hostWithTime.getHost())) {
                    neigh.remove(hostWithTime.getHost(), hostWithTime);
                }
            });


            Set<HostWithTime> subset = getRandomSubsetExcluding(neigh, subsetSize, hostWithTime);


            HostWithTime myself = new HostWithTime();

            myself.setAge(0);
            myself.setHost(self);

            subset.add(myself);

            sample = new HashSet<>(subset);
            List<Host> hstss = new LinkedList<>();
            List<Integer> age = new LinkedList<>();
            sample.forEach(hostWithTime1 -> {
                hstss.add(hostWithTime1.getHost());
                age.add(hostWithTime1.getAge());
            });

            openConnection(hostWithTime.getHost(), channelId);
            sendMessage(new ShuffleRequest(hstss, age), hostWithTime.getHost());
            logger.debug("Sent ShuffleRequest {}", hostWithTime.getHost());
        }
    }


    private void uponShuffleReply(ShuffleReply shuffleReply, Host host, short j, int i1) {
        logger.debug("Received shuffle reply from {}", host);
        Set<HostWithTime> joinDeser = new HashSet<>();
        for (int i = 0; i < shuffleReply.getSample().size(); i++) {
            HostWithTime hostWithTime = new HostWithTime();
            hostWithTime.setHost(shuffleReply.getSample().get(i));
            hostWithTime.setAge(shuffleReply.getAges().get(i));
            joinDeser.add(hostWithTime);
        }

        mergeView(joinDeser, sample);
    }

    private void uponShuffleRequest(ShuffleRequest shuffleRequest, Host host, short j, int i1) {
        logger.debug("Received ShuffleRequest {}", host);

        Set<HostWithTime> temporarySampleHostWithTimes = new HashSet<>(neigh.values());
//        HostWithTime me = new HostWithTime();
//        me.setHost(self); //o neigh ja la deve estar a priori?
//        me.setAge(0);
//        temporarySampleHostWithTimes.add(me);

        Set<HostWithTime> joinDeser = new HashSet<>();
        for (int i = 0; i < shuffleRequest.getSample().size(); i++) {
            HostWithTime hostWithTime = new HostWithTime();
            hostWithTime.setHost(shuffleRequest.getSample().get(i));
            hostWithTime.setAge(shuffleRequest.getAges().get(i));
            joinDeser.add(hostWithTime);
        }

        List<Host> hostToSend = new LinkedList<>();
        List<Integer> ages = new LinkedList<>();

        temporarySampleHostWithTimes.forEach(hostWithTime -> {
            hostToSend.add(hostWithTime.getHost());
            ages.add(hostWithTime.getAge());
        });
        sendMessage(new ShuffleReply(hostToSend, ages), host);
        logger.debug("Sent ShuffleReply {}", host);
        mergeView(joinDeser, temporarySampleHostWithTimes);
    }

    private void mergeView(Set<HostWithTime> peerSample, Set<HostWithTime> mySample) {

        peerSample.forEach(hostWithTime -> {
            if (!hostWithTime.getHost().equals(self)) {
                HostWithTime tofind = neigh.get(hostWithTime.getHost());
                if (tofind != null) {
                    if (tofind.equals(hostWithTime) && tofind.getAge() > hostWithTime.getAge()) {

                        neigh.remove(tofind.getHost(), tofind);
                        neigh.put(hostWithTime.getHost(), hostWithTime);

                    }


                } else if (neigh.size() < subsetSize) {

                    neigh.put(hostWithTime.getHost(), hostWithTime);

                } else {

                    HostWithTime matched = new HostWithTime();
                    matched.setAge(-1);

                    for(HostWithTime h : mySample){
                        for(HostWithTime h2 : neigh.values()){
                            if (h.equals(h2)) {
                                matched.setHost(h.getHost());
                                matched.setAge(h.getAge());
                                break;
                            }
                        }
                    }

                    HostWithTime h = null;

                    if (matched.getAge() == -1) {

                        h = getRandom(neigh);
                        neigh.remove(h.getHost());
                    } else {
                        neigh.remove(matched.getHost());

                    }


                    neigh.put(hostWithTime.getHost(), hostWithTime);
                }

            }
        });

        logger.debug("Finished Merge resulting in {}", neigh.keySet());
    }


    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        logger.debug("Connection to {} failed cause: {}", event.getNode(), event.getCause());
        boolean attempt = attempts.containsKey(event.getNode());

        if (attempt) {
            if (attempts.get(event.getNode()) < 1) {
                attempts.put(event.getNode(), attempts.get(event.getNode())+1);
            }else {

                attempts.remove(event.getNode());
                neigh.remove(event.getNode());
                logger.trace("Removed {} from neigh due to inactivity for a while", event.getNode());

            }
        }else {
            attempts.put(event.getNode(), 0);
        }
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());

        boolean attempt = attempts.containsKey(event.getNode());

        if (attempt) {
            if (attempts.get(event.getNode()) < 1) {
               attempts.put(event.getNode(), attempts.get(event.getNode())+1);
            }else {

                attempts.remove(event.getNode());
                neigh.remove(event.getNode());
                logger.trace("Removed {} from neigh due to inactivity for a while", event.getNode());

            }
        }else {
            attempts.put(event.getNode(), 0);
        }
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        HostWithTime tofind = neigh.get(event.getNode());
        if (tofind == null) {
            if(neigh.size()<subsetSize) {
                HostWithTime hostWithTime = new HostWithTime();
                hostWithTime.setAge(0);
                hostWithTime.setHost(event.getNode());
                neigh.put(hostWithTime.getHost(), hostWithTime);
//        openConnection(hostWithTime.getHost());
                logger.trace("Connection from {} is up", event.getNode());
            }
        }
    }

    private void uponMsgFail(ShuffleRequest msg, Host host, short destProto,
                             Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);

    }

    //If we passed a value >0 in the METRICS_INTERVAL_KEY property of the channel, this event will be triggered
    //periodically by the channel. This is NOT a protocol timer, but a channel event.
    //Again, we are just showing some of the information you can get from the channel, and use how you see fit.
    //"getInConnections" and "getOutConnections" returns the currently established connection to/from me.
    //"getOldInConnections" and "getOldOutConnections" returns connections that have already been closed.
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

    //If we setup the InfoTimer in the constructor, this event will be triggered periodically.
    //We are simply printing some information to present during runtime.
    private void uponInfoTime(InfoTimer timer, long timerId) {
        StringBuilder sb = new StringBuilder("Membership Metrics:\n");
        Set<Host> s = new HashSet<>();
        neigh.values().forEach(h -> s.add(h.getHost()));
        sb.append("Membership: ").append(s).append("\n");
//        sb.append("PendingMembership: ").append(pending).append("\n");
        //getMetrics returns an object with the number of events of each type processed by this protocol.
        //It may or may not be useful to you, but at least you know it exists.
        sb.append(getMetrics());
        logger.info(sb);
    }

    //Gets a random subset from the set of peers
    private static Set<HostWithTime> getRandomSubsetExcluding(Map<Host, HostWithTime> hostSet, int sampleSize, HostWithTime exclude) {


        List<HostWithTime> list = new LinkedList<>(hostSet.values());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getHost().equals(exclude.getHost())) {
                list.remove(i);
                break;
            }
        }
//        list.remove(exclude); ->pois com isto aqui nao precisava do if na merge view para ignorar-me a mim proprio :(
        Collections.shuffle(list);

        return new HashSet<>(list.subList(0, Math.min(sampleSize, list.size())));
    }

    private HostWithTime getOldest(Map<Host, HostWithTime> neigh) {

        //Initialize it with age 0 and no host
        HostWithTime oldest = new HostWithTime();
        oldest.setAge(0);

        neigh.keySet().forEach(host -> {

            if (neigh.get(host).getAge() > oldest.getAge()) {

                oldest.setHost(neigh.get(host).getHost());
                oldest.setAge(neigh.get(host).getAge());
            }
        });

        return oldest;
    }

    //Gets a random element from the set of peers
    private HostWithTime getRandom(Map<Host, HostWithTime> hostSet) {

        Object[] values = neigh.values().toArray();
        Object randomValue = values[rnd.nextInt(values.length)];

        return (HostWithTime) randomValue;
    }

    public Set<Host> getNeighbours() {
        Set<Host> hosts = new HashSet<>();
        neigh.values().forEach(hostWithTime -> hosts.add(hostWithTime.getHost()));
        return hosts;
    }


}
