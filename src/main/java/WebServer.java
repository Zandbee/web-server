import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServer {
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());

    public static void main(String[] args) {
        // TODO: read port from args
        int portNumber = 4444;

        logger.info("Starting a server");
        listen(portNumber);
    }

    private static void listen(int portNumber) {
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                new WebServerThread(serverSocket.accept()).run();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot listen on port " + portNumber, e);
        }
    }
}
