import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServer {
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());
    private static final String PROPERTIES_FILE = "config.properties";
    private static final int DEFAULT_PORT = 4444;

    public static void main(String[] args) {
        logger.info("Starting a server");
        listen(getPort());
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

    private static int getPort() {
        Properties properties = new Properties();
        int port = DEFAULT_PORT;
        //WebServer.class.getResourceAsStream()
        try (FileInputStream in = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(in);
            port = Integer.parseInt(properties.getProperty("port", Integer.toString(DEFAULT_PORT)));
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Properties file not found: " + PROPERTIES_FILE, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error processing " + PROPERTIES_FILE, e);
        }
        return port;
    }
}
