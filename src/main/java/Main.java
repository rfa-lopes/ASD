import babel.core.Babel;
import babel.core.GenericProtocol;
import network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.apps.BroadcastApp;
import protocols.broadcast.eagerpush.EagerPushBroadcast;
import protocols.broadcast.flood.FloodBroadcast;
import protocols.broadcast.plumtree.PlumTree;
import protocols.membership.cyclon.CyclonMembership;
import protocols.membership.full.SimpleFullMembership;
import protocols.membership.partial.HyParView;
import utils.InterfaceToIp;

import java.net.InetAddress;
import java.util.Properties;


public class Main {

    //Sets the log4j (logging library) configuration file
    static {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
    }

    //Creates the logger object
    private static final Logger logger = LogManager.getLogger(Main.class);

    //Default babel configuration file (can be overridden by the "-config" launch argument)
    private static final String DEFAULT_CONF = "babel_config.properties";

    ////Membership Algorithms
    private static final String CYCLON = "CYCLON";
    private static final String HYPARVIEW = "HYPARVIEW";
    private static final String FULL = "FULL";

    //Broadcast Algorithms
    private static final String PLUMTREE = "PLUMTREE";
    private static final String EAGER = "EAGER";
    private static final String FLOOD = "FLOOD";

    public static void main(String[] args) throws Exception {

        //Get the (singleton) babel instance
        Babel babel = Babel.getInstance();

        //Loads properties from the configuration file, and merges them with properties passed in the launch arguments
        Properties props = Babel.loadConfig(args, DEFAULT_CONF);

        //If you pass an interface name in the properties (either file or arguments), this wil get the IP of that interface
        //and create a property "address=ip" to be used later by the channels.
        InterfaceToIp.addInterfaceIp(props);

        //The Host object is an address/port pair that represents a network host. It is used extensively in babel
        //It implements equals and hashCode, and also includes a serializer that makes it easy to use in network messages
        Host myself = new Host(InetAddress.getByName(props.getProperty("address")),
                Integer.parseInt(props.getProperty("port")));

        logger.info("Hello, I am {}", myself);

        //Lets run the algorithms from props
        String broadcastAlgorithm = props.getProperty("broadcast");
        String membershipAlgorithm = props.getProperty("membership");

        // Application

        //Lets start by declaring that we will have two protocols - a membership one and a broadcast one
        GenericProtocol membership;
        GenericProtocol broadcast;
        BroadcastApp broadcastApp;

        //Initialize the protocol depending on the properties config file

        //Membership Algorithms
        switch (membershipAlgorithm.toUpperCase()) {
            case CYCLON:
                membership = new CyclonMembership(props, myself);
                break;
            case HYPARVIEW:
                membership = new HyParView(props, myself);
                break;
            default:
                membership = new SimpleFullMembership(props, myself);
                break;
        }

        //Broadcast Algorithms
        switch (broadcastAlgorithm.toUpperCase()) {
            case EAGER:
                broadcast = new EagerPushBroadcast(props, myself, membership);
                broadcastApp = new BroadcastApp(myself, props, EagerPushBroadcast.PROTOCOL_ID);
                break;
            case PLUMTREE:
                broadcast = new PlumTree(props, myself);
                broadcastApp = new BroadcastApp(myself, props, PlumTree.PROTOCOL_ID);
                break;
            default:
                broadcast = new FloodBroadcast(props, myself);
                broadcastApp = new BroadcastApp(myself, props, FloodBroadcast.PROTOCOL_ID);
                break;
        }

        //Register the protocols in babel
        //babel.registerProtocol(broadcastApp);
        //babel.registerProtocol(broadcast);
        babel.registerProtocol(membership);

        //Initialize the app and the algorithms
        //broadcastApp.init(props);
        //broadcast.init(props);
        membership.init(props);

        //Start babel and protocol threads
        babel.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));

    }

}
