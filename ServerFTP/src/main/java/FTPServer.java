import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {
    private int port = 21;
    private ServerSocket socket;
    boolean serverRunning = true;

    public static void main(String[] args) throws IOException {
        new FTPServer();
    }

    public FTPServer() throws IOException {
        try {
            socket = new ServerSocket(port);
        }
        catch (IOException e) {
            throw new IOException("Could not create server socket");
        }

        System.out.println("FTP Server started listening on port " + port);

        int noOfThreads = 0;

        while (serverRunning) {
            try {
                Socket client = socket.accept();

                // Port for incoming dataConnection (for passive mode) is the controlPort + number of created threads + 1
                int dataPort = port + noOfThreads + 1;

                // Create new worker thread for new connection
                Session session = new Session(client, dataPort);

                System.out.println("New connection received. Worker was created.");
                noOfThreads++;
                session.start();
            } catch (IOException e) {
                System.out.println("Exception encountered on accept");
                e.printStackTrace();
            }
        }

        try {
            socket.close();
            System.out.println("Server was stopped");
        } catch (IOException e) {
            throw new IOException("Problem stopping server");
        }
    }
}
