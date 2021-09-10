package fr.workshop.bank.transfer;
import bank.transfer.avro.UserBalance;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MediaType;

import static fr.workshop.bank.transfer.kafkastreams.KafkaStreamsApplicationUserBalance.BALANCE_VIEW;

@Path("/state")
public class UserBalanceServer implements Feature {

    private Server jettyServer;
    private KafkaStreams streams;

    public UserBalanceServer(){}

    public UserBalanceServer(KafkaStreams streams) {
        this.streams = streams;
    }


    @GET
    @Path("/balance/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    public Double userBalance(@PathParam("user") final String key) throws InterruptedException {
        try {
            // TODO 08
            System.out.println(streams.state());
            ReadOnlyKeyValueStore<String, UserBalance> keyValueStore = streams.store(BALANCE_VIEW, QueryableStoreTypes.keyValueStore());
            return keyValueStore.get(key).getAmount();
        } catch (InvalidStateStoreException ignored) {
            // store not yet ready for querying
            Thread.sleep(100);
            return -1d;
        }
    }


    public void start() throws Exception {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server();
        jettyServer.setHandler(context);

        final ResourceConfig rc = new ResourceConfig();
        rc.register(this);
        rc.register(JacksonFeature.class);

        final ServletContainer sc = new ServletContainer(rc);
        final ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");

        final ServerConnector connector = new ServerConnector(jettyServer);
        connector.setHost("localhost");
        connector.setPort(9090);
        jettyServer.addConnector(connector);

        context.start();

        try {
            jettyServer.start();
        } catch (final java.net.SocketException exception) {
            throw new RuntimeException(exception.toString());
        }
    }

    /**
     * Stop the Jetty Server
     * @throws Exception if jetty can't stop
     */
    public void stop() throws Exception {
        if (jettyServer != null) {
            jettyServer.stop();
        }
    }

    @Override
    public boolean configure(FeatureContext featureContext) {
        return false;
    }
}
