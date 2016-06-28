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
    private final String filesLocation;

    public WebServerThread(Socket socket, ConfigurationManager configuration) {
        System.out.println("NEW THREAD");
        this.socket = socket;
        this.configuration = configuration;
        filesLocation = URI_SCHEME_FILE + this.configuration.getHostDir();
    }


    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             OutputStream os = socket.getOutputStream()) {

            String requestLine = in.readLine(); // cannot read it later as it will be already read with headers

            String sessionId = getSessionId(in);
            if (sessionId != null && isSessionExpired(sessionId)) {
                //respondForbidden(os, sessionId);
                respondWithStatus(HttpCode.HTTP_403, os, null, sessionId);
                return;
            }
            logger.info("Session OK or no session id");

            if (requestLine != null && requestLine.startsWith(REQUEST_GET)) {
                String path = getGetRequestedFilePath(requestLine);
                logger.info("Requested path: " + path);

                respond(os, path, sessionId);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    private String getSessionId(BufferedReader in) throws IOException {
        String headerLine;
        String sessionId = null;
        while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
            if (headerLine.startsWith(HEADER_COOKIE) && headerLine.contains(COOKIE_SESSION_ID)) {
                // there is session id for this client
                sessionId = getSessionIdFromCookie(headerLine);
                logger.info("Session ID = " + String.valueOf(sessionId));
            }
        }
        return sessionId;
    }

    private String getGetRequestedFilePath(String requestLine) {
        int pathStart = requestLine.indexOf(" ") + 1;
        int pathFinish = requestLine.indexOf(" ", pathStart);
        return requestLine.substring(pathStart, pathFinish);
    }

    private void respond(OutputStream os, String path, String sessionId) throws IOException {
        switch (path) {
            case "/":
                // return index.html
                //respondOk(os, toFileUri(HTML_INDEX), sessionId);
                respondWithStatus(HttpCode.HTTP_200, os, toFileUri(HTML_INDEX), sessionId);
                break;
            default:
                // return requested file
                URI fileUri = URI.create(filesLocation + path);
                if (Files.exists(Paths.get(fileUri))) {
                    //respondOk(os, fileUri, sessionId);
                    respondWithStatus(HttpCode.HTTP_200, os, fileUri, sessionId);
                } else {
                    //respondFileNotFound(os, sessionId);
                    respondWithStatus(HttpCode.HTTP_404, os, null, sessionId);
                }
                break;
        }
    }

    private void respondWithStatus(HttpCode code, OutputStream os, URI fileUri, String sessionId) throws IOException {
        BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));

        // log response status
        logger.info("WRITE LOG");
        writeHttpStatusLogMessage(code);

        // write response status
        logger.info("WRITE STATUS: " + code.getStatus());
        bw.write(code.getStatus());
        bw.newLine();

        // write response headers
        logger.info("WRITE HEADERS");
        writeResponseHeaders(bw, sessionId);

        // write file to response
        logger.info("CHECK FILE");
        if (fileUri == null) {
            fileUri = getStatusPageFileUri(code);
        }
        logger.info("WRITE FILE");
        sendFileInResponse(os, fileUri);
    }

    private static void writeHttpStatusLogMessage(HttpCode code) throws IOException {
        switch (code) {
            case HTTP_200:
                logger.info("RESPONDING");
                break;
            case HTTP_403:
                logger.info("RESPONDING - Forbidden/Session expired");
                break;
            case HTTP_404:
                logger.info("RESPONDING - File not found");
                break;
            case HTTP_503:
                logger.info("RESPONDING - Unavailable");
                break;
            default:
                logger.info("RESPONDING - UNKNOWN HTTP CODE");
                break;
        }
    }

    private URI getStatusPageFileUri(HttpCode code) throws IOException {
        switch (code) {
            case HTTP_200:
                // file already known, should not get here
                return toFileUri(HTML_NOT_FOUND); //TODO
            case HTTP_403:
                return toFileUri(HTML_FORBIDDEN);
            case HTTP_404:
                return toFileUri(HTML_NOT_FOUND);
            case HTTP_503:
                return toFileUri(HTML_UNAVAILABLE);
            default:
                return toFileUri(HTML_NOT_FOUND);//TODO
        }
    }

   /* private void respondFileNotFound(OutputStream os, String sessionId) throws IOException {
        BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));

        logger.info("RESPONDING - File not found");
        bw.write(HTTP_404);
        bw.newLine();

        writeResponseHeaders(os, sessionId);

        sendFileInResponse(os, toFileUri(HTML_NOT_FOUND));
    }

    private void respondOk(OutputStream os, URI fileUri, String sessionId) throws IOException {
        BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));

        logger.info("RESPONDING");
        bw.write(HTTP_200);
        bw.newLine();

        writeResponseHeaders(os, sessionId);

        sendFileInResponse(os, fileUri);
    }

    private void respondForbidden(OutputStream os, String sessionId) throws IOException {
        BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));

        logger.info("RESPONDING - Forbidden/Session expired");
        bw.write(HTTP_403);
        bw.newLine();

        writeResponseHeaders(os, sessionId);

        sendFileInResponse(os, toFileUri(HTML_FORBIDDEN));
    }

    private void respondUnavailable(OutputStream os, String sessionId) throws IOException {
        BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));

        logger.info("RESPONDING - Unavailable");
        bw.write(HTTP_503);
        bw.newLine();

        writeResponseHeaders(os, sessionId);

        sendFileInResponse(os, toFileUri(HTML_UNAVAILABLE));
    }*/

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

    private void sendFileInResponse(OutputStream os, URI fileUri) throws IOException {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(new File(fileUri.getPath())))) {
            byte bytes[] = new byte[1024]; // TODO
            while (is.read(bytes) != -1) {
                os.write(bytes);
            }
            /*BufferedWriter bw = new BufferedWriter(new PrintWriter(os, true));
            bw.newLine();*/
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
        return URI.create(filesLocation + fileName);
    }

    private enum HttpCode {
        HTTP_200 ("HTTP/1.1 200 OK"),
        HTTP_403 ("HTTP/1.1 403 Forbidden"),
        HTTP_404 ("HTTP/1.1 404 File not found"),
        HTTP_503 ("HTTP/1.1 503 Service Unavailable");

        private final String status;

        HttpCode(String status) {
            this.status = status;
        }

        private String getStatus() {
            return status;
        }
    }
}
