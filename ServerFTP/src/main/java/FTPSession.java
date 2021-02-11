import java.net.ServerSocket;
import java.net.Socket;

public class FTPSession extends Thread {
    private boolean debug = true;
    private String root;
    private String currDirectory;
    private Socket controlSocket;
    private ServerSocket dataSocket;
    private int dataPort;

    public FTPSession(Socket client, int dataPort)
    {
        super();
        this.controlSocket = client;
        this.dataPort = dataPort;
        this.currDirectory = System.getProperty("user.dir") + "/test";
        this.root = System.getProperty("user.dir");
    }
}
