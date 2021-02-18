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

        int numThreads = 0;

        while (serverRunning) {
            try {
                Socket client = socket.accept();

                // Attribution d'un port pour chaque Session (Thread) qui va chercher à se connecter
                int dataPort = port + numThreads + 1;

                // Création d'une session qui va se connecter à notre serveur
                FTPSession FTPSession = new FTPSession(client, dataPort);

                System.out.println("New session created");
                numThreads++;
                FTPSession.start();
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
