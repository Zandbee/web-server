import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServer {
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());

    private static final String PROPERTIES_FILE = "config.properties";

    public static void main(String[] args) {
        logger.info("Starting a server");
        listen();
    }

    private static void listen() {
        ConfigurationManager configuration = ConfigurationManager.getConfiguration(PROPERTIES_FILE);
        int port = configuration.getPort();
        WebServerConnectionsCounter counter = new WebServerConnectionsCounter();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                System.out.println("START NEW THREAD");
                try (Socket socket = serverSocket.accept()) {
                    new WebServerThread(socket, configuration, counter).start();
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot listen on port " + port, e);
        }
    }
}
