import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServerThread extends Thread {
    private static final String CLASS_NAME = WebServerThread.class.getName();
    private static final Logger logger = Logger.getLogger(CLASS_NAME);
    private static final String UTF_8 = "UTF-8";
    private static final String REQUEST_GET = "GET";

    private static final String HEADER_DATE = "Date: ";
    private static final String HEADER_SERVER = "Server: ";
    private static final String HEADER_SET_COOKIE = "Set-Cookie: ";
    private static final String HEADER_COOKIE = "Cookie: ";
    private static final String COOKIE_SESSION_ID = "SESSID=";

    private static final String URI_SCHEME_FILE = "file:///";
    private static final String HTML_INDEX = "/index.html";
    private static final String HTML_FORBIDDEN = "/forbidden.html";
    private static final String HTML_NOT_FOUND = "/notfound.html";
    private static final String SERVER_NAME = "noname.server.ru";

    private Socket socket;
    private ConfigurationManager configuration;
    private String sessionId = null;
    private static ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
    private boolean sessionExpired = false;

    public WebServerThread(Socket socket, ConfigurationManager configuration) {
        super(CLASS_NAME);
        System.out.println("NEW THREAD");
        this.socket = socket;
        this.configuration = configuration;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             BufferedWriter bw = new BufferedWriter(new PrintWriter(socket.getOutputStream(), true))) {

            String requestLine = in.readLine();
            String headerLine;

            while (!(headerLine = in.readLine()).isEmpty()) {
                if (headerLine.startsWith(HEADER_COOKIE) && headerLine.contains(COOKIE_SESSION_ID)) {

                    // there is session id for this client
                    sessionId = getSessionIdFromCookie(headerLine);

                    System.out.println("Session ID = " + String.valueOf(sessionId));

                    String sessionStartTimeString = sessionMap.get(sessionId);
                    System.out.println("Session start time String = " + sessionStartTimeString);
                    if (sessionStartTimeString != null) {
                        long sessionStartTime = Long.parseLong(sessionStartTimeString);
                        long currentTime = System.currentTimeMillis();
                        System.out.println("Cur time = " + currentTime + ", session start = " + sessionStartTime);
                        if ((currentTime - sessionStartTime) / 1000 >= configuration.getSessionInterval()) {
                            // session id expired - respondForbidden, set new session in writeResponseHeaders, delete old session, return
                            System.out.println("Session expired");
                            setSessionExpired(true);
                            respondForbidden(bw);
                            return;
                        } else {
                            // session id is ok
                            System.out.println("Session is Ok");
                            setSessionExpired(false);
                        }
                    } else {
                        // there is no such session id in the map
                        System.out.println("No session in map. Set expired");
                        setSessionExpired(true);
                    }
                }
            }

            System.out.println("Session OK or no session id");
            if (requestLine != null && requestLine.startsWith(REQUEST_GET)) {
                int pathStart = requestLine.indexOf(" ") + 1;
                int pathFinish = requestLine.indexOf(" ", pathStart);
                String path = requestLine.substring(pathStart, pathFinish);
                System.out.println("Requested path: " + path);

                respond(bw, path);
            }

            //socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    private String getSessionIdFromCookie(String headerCookieLine) {
        int cookieSessionIdPosition = headerCookieLine.indexOf(COOKIE_SESSION_ID) + COOKIE_SESSION_ID.length();
        int cookieSessionIdEndPosition = headerCookieLine.indexOf(";", cookieSessionIdPosition);

        if (cookieSessionIdEndPosition > 0) {
            return headerCookieLine.substring(cookieSessionIdPosition, cookieSessionIdEndPosition);
        } else {
            return headerCookieLine.substring(cookieSessionIdPosition);
        }
    }

    private void respond(BufferedWriter bw, String path) throws IOException {
        switch (path) {
            case "/":
                // return index.html
                respondOk(bw, URI.create(URI_SCHEME_FILE + configuration.getHost() + HTML_INDEX));
                break;
            default:
                // return requested file
                URI fileUri = URI.create(URI_SCHEME_FILE + configuration.getHost() + path);
                System.out.println("File URI: " + fileUri);
                if (Files.exists(Paths.get(fileUri))) {
                    respondOk(bw, fileUri);
                } else {
                    respondFileNotFound(bw);
                }
                break;
        }
    }

    private void respondFileNotFound(BufferedWriter bw) throws IOException {
        System.out.println("RESPONDING - File not found");
        bw.write("HTTP/1.1 404 File not found");
        bw.newLine();

        writeResponseHeaders(bw);

        sendFileInResponse(bw, URI.create(URI_SCHEME_FILE + configuration.getHost() + HTML_NOT_FOUND));
    }

    private void respondOk(BufferedWriter bw, URI fileUri) throws IOException {
        System.out.println("RESPONDING");
        bw.write("HTTP/1.1 200 OK");
        bw.newLine();

        writeResponseHeaders(bw);

        sendFileInResponse(bw, fileUri);
    }

    private void respondForbidden(BufferedWriter bw) throws IOException {
        System.out.println("RESPONDING - Forbidden/Session expired");
        bw.write("HTTP/1.1 403 Forbidden");
        bw.newLine();

        writeResponseHeaders(bw);

        sendFileInResponse(bw, URI.create(URI_SCHEME_FILE + configuration.getHost() + HTML_FORBIDDEN));
    }

    // TODO: can forget insert this in some respond... method
    private void writeResponseHeaders(BufferedWriter bw) throws IOException {
        // any server information
        bw.write(HEADER_SERVER + SERVER_NAME);
        bw.newLine();

        // current server time
        SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        bw.write(HEADER_DATE + httpDateFormat.format(new Date()));
        bw.newLine();

        if (sessionId == null || isSessionExpired()) {
            // generate new session id and add it to sessionMap
            // after all, set session expired false
            System.out.println("Write cookie session header");
            sessionId = generateSessionId();
            sessionMap.put(sessionId, String.valueOf(System.currentTimeMillis()));
            setSessionExpired(false);

            bw.write(HEADER_SET_COOKIE + COOKIE_SESSION_ID + sessionId);
            bw.newLine();
        }

        bw.newLine(); // empty line after headers
    }

    private void sendFileInResponse(BufferedWriter bw, URI fileUri) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(fileUri.getPath())))) {
            String fileLine;
            while ((fileLine = br.readLine()) != null) {
                bw.write(fileLine);
                bw.newLine();
            }
            bw.newLine(); // TODO: why need this line
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Cannot find " + fileUri.getPath(), e);
        }
    }

    private static String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private void setSessionExpired(boolean sessionExpired) {
        if (sessionExpired) {
            sessionMap.remove(sessionId);
        }
        this.sessionExpired = sessionExpired;
    }

    private boolean isSessionExpired() {
        return sessionExpired;
    }
}
