import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
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

    private static int COUNT = 0;

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
                executor.submit(new WebServerThread(serverSocket.accept(), configuration)).get();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot listen on port " + port, e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Thread was interrupted", e.getCause());
        } catch (ExecutionException e) {
            logger.log(Level.SEVERE, "Thread execution aborted with exception", e.getCause());
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                logger.warning("Server is shutting down");
                if (executor != null) {
                    logger.warning("Executor is shutting down");
                    executor.shutdown();
                }
                logger.warning("Finally shutting down");
            }
        });
    }
}
