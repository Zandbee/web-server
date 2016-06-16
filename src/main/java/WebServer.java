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
    private static final String PROPERTIES_KEY_PORT = "port";
    private static final String PROPERTIES_KEY_HOST = "host";
    private static final String PROPERTIES_KEY_SESSION_INTERVAL = "session_interval_seconds";

    private static final String DEFAULT_PORT = "4444";
    private static final String DEFAULT_HOST = "D:/web-server";
    private static final String DEFAULT_SESSION_INTERVAL = "60";

    public static void main(String[] args) {
        logger.info("Starting a server");

        // use port and host from config file
        Properties properties = getPropertiesList();
        listen(getPort(properties), getHost(properties), getSessionInterval(properties));
    }

    private static void listen(int portNumber, String homeDir, int sessionInterval) {
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                System.out.println("START NEW THREAD");
                new WebServerThread(serverSocket.accept(), homeDir, sessionInterval).run();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot listen on port " + portNumber, e);
        }
    }

    private static int getPort(Properties properties) {
        return Integer.parseInt(properties.getProperty(PROPERTIES_KEY_PORT, DEFAULT_PORT));
    }

    private static String getHost(Properties properties) {
        return properties.getProperty(PROPERTIES_KEY_HOST, DEFAULT_HOST);
    }

    private static int getSessionInterval(Properties properties) {
        return Integer.parseInt(properties.getProperty(PROPERTIES_KEY_SESSION_INTERVAL, DEFAULT_SESSION_INTERVAL));
    }

    private static Properties getPropertiesList() {
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(in);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Properties file not found: " + PROPERTIES_FILE, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error processing " + PROPERTIES_FILE, e);
        }

        return properties;
    }
}
