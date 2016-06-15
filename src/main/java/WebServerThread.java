import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by vstrokova on 08.06.2016.
 */
public class WebServerThread extends Thread {
    private static final Logger logger = Logger.getLogger(WebServerThread.class.getName());
    private static final String UTF_8 = "UTF-8";
    private static final String REQUEST_GET = "GET";
    private static final String URI_SCHEME_FILE = "file:";
    private static final String URI_HOST = "///D:/web-server";
    private static final String INDEX_HTML = "/index.html";
    private Socket socket;

    public WebServerThread(Socket socket) {
        //super("WebServerThread"); TODO
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             OutputStream out = socket.getOutputStream()) {
            String requestLine = in.readLine();

            if (requestLine != null && requestLine.startsWith(REQUEST_GET)) {
                int pathStart = requestLine.indexOf(" ") + 1;
                int pathFinish = requestLine.indexOf(" ", pathStart);
                String path = requestLine.substring(pathStart, pathFinish);
                System.out.println("Requested path: " + path);

                respond(out, path);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading from or writing to the socket", e);
        }
    }

    // TODO: send socket/stream, not bw
    private void respond(OutputStream out, String path) {
        switch (path) {
            case "/":
                //return index.html
                respondOk(out, URI.create(URI_SCHEME_FILE + URI_HOST + INDEX_HTML));
                break;
            default:
                URI fileUri = URI.create(URI_SCHEME_FILE + URI_HOST + path);
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
            bw.newLine();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to the socket", e);
        }
    }

    private void respondOk(OutputStream out, URI fileUri) {
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(out))) {
            System.out.println("RESPONDING");
            bw.write("HTTP/1.1 200 OK");
            bw.newLine(); // empty line after status line
            bw.newLine(); // empty line after headers

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
}
