import java.io.*;
import java.net.*;
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
    private static final String HTML_FORBIDDEN = "/forbidden.html";
    private static final String HTML_NOT_FOUND = "/notfound.html";
    private static final String SERVER_NAME = "noname.server.ru";

    private Socket socket;
    private String host; // files are stored here
    private int sessionInterval;


    public WebServerThread(Socket socket, String host, int sessionInterval) {
        //super("WebServerThread"); TODO
        System.out.println("NEW THREAD");
        this.socket = socket;
        this.host = host;
        this.sessionInterval = sessionInterval;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             BufferedWriter bw = new BufferedWriter(new PrintWriter(socket.getOutputStream(), true))) {

            String requestLine = in.readLine();
            if (requestLine != null && requestLine.startsWith(REQUEST_GET)) {
                int pathStart = requestLine.indexOf(" ") + 1;
                int pathFinish = requestLine.indexOf(" ", pathStart);
                String path = requestLine.substring(pathStart, pathFinish);
                System.out.println("Requested path: " + path);

                setCookieUsingCookieHandler();
                respond(bw, path);
            }

            //socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    private void respond(BufferedWriter bw, String path) throws IOException {
        switch (path) {
            case "/":
                // return index.html
                respondOk(bw, URI.create(URI_SCHEME_FILE + host + HTML_INDEX));
                break;
            default:
                // return requested file
                URI fileUri = URI.create(URI_SCHEME_FILE + host + path);
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
        bw.newLine(); // empty line after status line

        writeResponseHeaders(bw);

        sendFileInResponse(bw, URI.create(URI_SCHEME_FILE + host + HTML_NOT_FOUND));
    }

    private void respondOk(BufferedWriter bw, URI fileUri) throws IOException {
        System.out.println("RESPONDING");
        bw.write("HTTP/1.1 200 OK");
        bw.newLine(); // empty line after status line

        writeResponseHeaders(bw);

        sendFileInResponse(bw, fileUri);
    }

    private void respondForbidden(BufferedWriter bw) throws IOException {
        System.out.println("RESPONDING - Forbidden/Session expired");
        bw.write("HTTP/1.1 403 Forbidden");
        bw.newLine(); // empty line after status line

        writeResponseHeaders(bw);

        sendFileInResponse(bw, URI.create(URI_SCHEME_FILE + host + HTML_FORBIDDEN));
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

    private void sendFileInResponse(BufferedWriter bw, URI fileUri) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(new File(fileUri.getPath())))) {
            String fileLine;
            while ((fileLine = br.readLine()) != null) {
                bw.write(fileLine);
                bw.newLine();
            }
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Cannot find " + fileUri.getPath(), e);
        }
    }

    private void setCookieUsingCookieHandler() {
        try {
            // instantiate CookieManager
            CookieManager manager = new CookieManager();
            CookieHandler.setDefault(manager);
            CookieStore cookieJar =  manager.getCookieStore();

            // create cookie
            HttpCookie cookie = new HttpCookie("UserName", "John Doe");

            // add cookie to CookieStore for a
            // particular URL
            URL url = new URL("http://localhost:4444");
            cookieJar.add(url.toURI(), cookie);
            System.out.println("Added cookie using cookie handler");
        } catch(Exception e) {
            System.out.println("Unable to set cookie using CookieHandler");
            e.printStackTrace();
        }
    }
}
