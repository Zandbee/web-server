import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by VEsNA on 21.06.2016.
 */
public class ConfigurationManager {
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());

    private static final String PROPERTIES_KEY_PORT = "port";
    private static final String PROPERTIES_KEY_HOST = "host";
    private static final String PROPERTIES_KEY_SESSION_INTERVAL = "session_interval_seconds";

    private static final String DEFAULT_PORT = "4444";
    private static final String DEFAULT_HOST = "D:/web-server";
    private static final String DEFAULT_SESSION_INTERVAL = "60";

    private int port;
    private String host;
    private int sessionInterval;

    public ConfigurationManager(String configurationFile) {
        Properties properties = getPropertiesList(configurationFile);

        this.port = getPortFromPropeties(properties);
        this.host = getHostFromProperties(properties);
        this.sessionInterval = getSessionIntervalFromProperties(properties);
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public int getSessionInterval() {
        return sessionInterval;
    }

    private static int getPortFromPropeties(Properties properties) {
        return Integer.parseInt(properties.getProperty(PROPERTIES_KEY_PORT, DEFAULT_PORT));
    }

    private static String getHostFromProperties(Properties properties) {
        return properties.getProperty(PROPERTIES_KEY_HOST, DEFAULT_HOST);
    }

    private static int getSessionIntervalFromProperties(Properties properties) {
        return Integer.parseInt(properties.getProperty(PROPERTIES_KEY_SESSION_INTERVAL, DEFAULT_SESSION_INTERVAL));
    }

    private static Properties getPropertiesList(String propertiesFile) {
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            properties.load(in);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Properties file not found: " + propertiesFile, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error processing " + propertiesFile, e);
        }

        return properties;
    }
}
