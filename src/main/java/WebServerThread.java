import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServerThread implements Runnable {
    private static final Logger logger = Logger.getLogger(WebServerThread.class.getName());

    private static final String UTF_8 = "UTF-8";
    private static final String REQUEST_GET = "GET";

    private static final String HTTP_404 = "HTTP/1.1 404 File not found";
    private static final String HTTP_200 = "HTTP/1.1 200 OK";
    private static final String HTTP_403 = "HTTP/1.1 403 Forbidden";
    private static final String HTTP_503 = "HTTP/1.1 503 Service Unavailable";

    private static final String HEADER_DATE = "Date: ";
    private static final String HEADER_SERVER = "Server: ";
    private static final String HEADER_SET_COOKIE = "Set-Cookie: ";
    private static final String HEADER_COOKIE = "Cookie: ";
    private static final String COOKIE_SESSION_ID = "SESSID=";

    private static final String URI_SCHEME_FILE = "file:///";
    private static final String HTML_INDEX = "/index.html";
    private static final String HTML_FORBIDDEN = "/forbidden.html";
    private static final String HTML_NOT_FOUND = "/notfound.html";
    private static final String HTML_UNAVAILABLE = "/unavailable.html";

    private static final String HTTP_DATE_TIME_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final String SERVER_NAME = "noname.server.ru";

    private Socket socket;
    private ConfigurationManager configuration;
    private static ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
    private boolean sessionExpired = false;

    public WebServerThread(Socket socket, ConfigurationManager configuration) {
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
            String sessionId = null; // TODO: passing it from one to another all the time

            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith(HEADER_COOKIE) && headerLine.contains(COOKIE_SESSION_ID)) {
                    // there is session id for this client
                    sessionId = getSessionIdFromCookie(headerLine);
                    logger.info("Session ID = " + String.valueOf(sessionId));

                    if (isSessionExpired(sessionId)) {
                        respondForbidden(bw, sessionId);
                        return;
                    }
                }
            }

            logger.info("Session OK or no session id");
            if (requestLine != null && requestLine.startsWith(REQUEST_GET)) {
                String path = getGetRequestedFilePath(requestLine);
                logger.info("Requested path: " + path);

                respond(bw, path, sessionId);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    private String getGetRequestedFilePath(String requestLine) {
        int pathStart = requestLine.indexOf(" ") + 1;
        int pathFinish = requestLine.indexOf(" ", pathStart);
        return requestLine.substring(pathStart, pathFinish);
    }

    private void respond(BufferedWriter bw, String path, String sessionId) throws IOException {
        switch (path) {
            case "/":
                // return index.html
                respondOk(bw, toFileUri(HTML_INDEX), sessionId);
                break;
            default:
                // return requested file
                URI fileUri = URI.create(URI_SCHEME_FILE + configuration.getHost() + path);
                if (Files.exists(Paths.get(fileUri))) {
                    respondOk(bw, fileUri, sessionId);
                } else {
                    respondFileNotFound(bw, sessionId);
                }
                break;
        }
    }

    private void respondFileNotFound(BufferedWriter bw, String sessionId) throws IOException {
        logger.info("RESPONDING - File not found");
        bw.write(HTTP_404);
        bw.newLine();

        writeResponseHeaders(bw, sessionId);

        sendFileInResponse(bw, toFileUri(HTML_NOT_FOUND));
    }

    private void respondOk(BufferedWriter bw, URI fileUri, String sessionId) throws IOException {
        logger.info("RESPONDING");
        bw.write(HTTP_200);
        bw.newLine();

        writeResponseHeaders(bw, sessionId);

        sendFileInResponse(bw, fileUri);
    }

    private void respondForbidden(BufferedWriter bw, String sessionId) throws IOException {
        logger.info("RESPONDING - Forbidden/Session expired");
        bw.write(HTTP_403);
        bw.newLine();

        writeResponseHeaders(bw, sessionId);

        sendFileInResponse(bw, toFileUri(HTML_FORBIDDEN));
    }

    private void respondUnavailable(BufferedWriter bw, String sessionId) throws IOException {
        logger.info("RESPONDING - Unavailable");
        bw.write(HTTP_503);
        bw.newLine();

        writeResponseHeaders(bw, sessionId);

        sendFileInResponse(bw, toFileUri(HTML_UNAVAILABLE));
    }

    // TODO: can forget insert this in some respond... method
    private void writeResponseHeaders(BufferedWriter bw, String sessionId) throws IOException {
        // server name header
        bw.write(HEADER_SERVER + SERVER_NAME);
        bw.newLine();

        // server time header
        SimpleDateFormat httpDateFormat = new SimpleDateFormat(HTTP_DATE_TIME_PATTERN, Locale.ENGLISH);
        bw.write(HEADER_DATE + httpDateFormat.format(new Date()));
        bw.newLine();

        // session id header
        if (sessionId == null || isSessionExpired(sessionId)) {
            // generate new session id and add it to sessionMap
            // after all, set session expired false
            logger.info("Write cookie session header");
            sessionId = generateSessionId();
            logger.info("NEW SESSION GENERATED: " + sessionId);
            sessionMap.put(sessionId, String.valueOf(System.currentTimeMillis()));
            setSessionExpired(false, sessionId);

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
            bw.newLine();
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Cannot find " + fileUri.getPath(), e);
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

    private static String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private void setSessionExpired(boolean sessionExpired, String sessionId) {
        if (sessionExpired) {
            sessionMap.remove(sessionId);
        }
        this.sessionExpired = sessionExpired;
    }

    private boolean isSessionExpired(String sessionId) {
        String sessionStartTimeString = sessionMap.get(sessionId);
        if (sessionStartTimeString != null) {
            long sessionStartTime = Long.parseLong(sessionStartTimeString);
            long currentTime = System.currentTimeMillis();
            logger.info("Cur time - session start = " + (currentTime - sessionStartTime));

            if ((currentTime - sessionStartTime) / 1000 >= configuration.getSessionInterval()) {
                // session id expired - respondForbidden, set new session in writeResponseHeaders, delete old session, return
                logger.info("Session expired");
                setSessionExpired(true, sessionId);
            } else {
                // session id is ok
                logger.info("Session is Ok");
                setSessionExpired(false, sessionId);
            }
        } else {
            // there is no such session id in the map
            logger.info("No session in map. Set expired: " + sessionId);
            setSessionExpired(true, sessionId);
        }

        return this.sessionExpired;
    }

    private URI toFileUri(String fileName) throws IOException {
        return URI.create(URI_SCHEME_FILE + configuration.getHost() + fileName);
    }
}
