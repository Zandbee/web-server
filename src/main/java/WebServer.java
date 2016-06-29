import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServer {
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());

    private static final String PROPERTIES_FILE = "config.properties";

    private static ExecutorService executor;

    public static void main(String[] args) {
        registerShutdownHook();
        listen();
    }

    private static void listen() {
        logger.info("Server started");
        ConfigurationManager configuration = ConfigurationManager.getConfiguration(PROPERTIES_FILE);
        int port = configuration.getPort();
        executor = Executors.newFixedThreadPool(configuration.getMaxConnectionsNumber());
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!executor.isShutdown()) {
                executor.execute(new WebServerThread(serverSocket.accept(), configuration));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot listen on port " + port, e);
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                logger.warning("Server is shutting down");
                if (executor != null) {
                    executor.shutdown();
                }
            }
        });
    }
}
