import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Classe pour représenter le serveur FTP
 * Elle va permettre de gérer les différentes connexions entrantes
 */
public class FTPServer {
    /**
     * Port pour se connecter au FTP
     * Pour pouvoir se connecter valable avec IntelliJ nous devons utiliser une valeur > à 1024
     * (ref = https://www.jetbrains.com/help/idea/settings-debugger.html)
     */
    private int port = 1025;
    private ServerSocket socket;
    boolean serverRunning = true;

    /**
     * Main pour lancer notre programme
     * @param args Argument non pris en compte
     * @throws IOException Problème lors du lancement du serveur
     */
    public static void main(String[] args) throws IOException {
        new FTPServer();
    }

    /**
     * Constructeur pour le serveur afin de créer le socket serveur et attendre des connexions
     * @throws IOException
     */
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

        //Fermeture du socket serveur
        try {
            socket.close();
            System.out.println("Server was stopped");
        } catch (IOException e) {
            throw new IOException("Problem stopping server");
        }
    }
}
