import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServerThread extends Thread {
    private static final Logger logger = Logger.getLogger(WebServerThread.class.getName());
    private static final String UTF_8 = "UTF-8";
    private static final String REQUEST_GET = "GET";

    private static final String HEADER_DATE = "Date: ";
    private static final String HEADER_SERVER = "Server: ";

    private static final String URI_SCHEME_FILE = "file:///";
    private static final String HTML_INDEX = "/index.html";
    private static final String HTML_FORBIDDEN = "/index.html";
    private static final String SERVER_NAME = "noname.server.ru";

    private Socket socket;
    private String host; // files are stored here
    private int sessionInterval;
    private long sessionStartTime;
    private long currentTime;

    public WebServerThread(Socket socket, String host, int sessionInterval) {
        //super("WebServerThread"); TODO
        System.out.println("NEW THREAD");
        this.socket = socket;
        this.host = host;
        this.sessionInterval = sessionInterval;
        this.sessionStartTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             OutputStream out = socket.getOutputStream()) {
            String requestLine;

            while ((requestLine = in.readLine()) != null) {
                // check if a session has not ended yet
                /*long curTime = System.currentTimeMillis();
                int sessionTime = (int) (curTime - sessionStartTime);
                System.out.println("Start time: " + sessionStartTime);
                System.out.println("Current time: " + curTime);
                System.out.println("Session time: " + sessionTime);
                if (sessionTime > sessionInterval) {
                    // session timeout
                    respondForbidden(out);
                    return;
                }*/

                if (requestLine.startsWith(REQUEST_GET)) {
                    int pathStart = requestLine.indexOf(" ") + 1;
                    int pathFinish = requestLine.indexOf(" ", pathStart);
                    String path = requestLine.substring(pathStart, pathFinish);
                    System.out.println("Requested path: " + path);

                    respond(out, path);
                }
            }
            System.out.println("Null in request line");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    private void respond(OutputStream out, String path) {
        switch (path) {
            case "/":
                // return index.html
                respondOk(out, URI.create(URI_SCHEME_FILE + host + HTML_INDEX));
                break;
            default:
                // return requested file
                URI fileUri = URI.create(URI_SCHEME_FILE + host + path);
                System.out.println("File URI: " + fileUri);
                if (Files.exists(Paths.get(fileUri))) {
                    respondOk(out, fileUri);
                } else {
                    respondFileNotFound(out);
                }
                break;
        }
    }

    private void respondFileNotFound(OutputStream out) {
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(out))) {
            System.out.println("RESPONDING - File not found");
            bw.write("HTTP/1.1 404 File not found");
            bw.newLine(); // empty line after status line

            writeResponseHeaders(bw);

            bw.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to the socket", e);
        }
    }

    private void respondOk(OutputStream out, URI fileUri) {
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(out))) {
            System.out.println("RESPONDING");
            bw.write("HTTP/1.1 200 OK");
            bw.newLine(); // empty line after status line

            writeResponseHeaders(bw);

            BufferedReader br = new BufferedReader(new FileReader(new File(fileUri.getPath())));
            String fileLine;
            while ((fileLine = br.readLine()) != null) {
                bw.write(fileLine);
                bw.newLine();
            }

            //bw.newLine(); // TODO ??
            bw.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to the socket", e);
        }
    }

    private void respondForbidden(OutputStream out) {
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(out))) {
            System.out.println("RESPONDING - Forbidden/Session expired");
            bw.write("HTTP/1.1 403 Forbidden");
            bw.newLine(); // empty line after status line

            writeResponseHeaders(bw);

            BufferedReader br = new BufferedReader(new FileReader(new File(URI.create(URI_SCHEME_FILE + host + HTML_FORBIDDEN))));
            String fileLine;
            while ((fileLine = br.readLine()) != null) {
                bw.write(fileLine);
                bw.newLine();
            }

            bw.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to the socket", e);
        }
    }

    // TODO: can forget insert this in some respond... method
    private void writeResponseHeaders(BufferedWriter bw) throws IOException { // TODO: static?
        // any server information
        bw.write(HEADER_SERVER + SERVER_NAME);
        bw.newLine();

        // current server time
        SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        bw.write(HEADER_DATE + httpDateFormat.format(new Date()));
        bw.newLine();

        bw.newLine(); // empty line after headers
    }
}
