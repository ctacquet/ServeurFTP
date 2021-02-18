import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class FTPSession extends Thread {
    private enum userState {NOTCONNECTED, PASSWORDNEEDED, CONNECTED}

    //Debug pour afficher toutes les commandes exécutées
    private boolean debug = true;
    //Variables liées au chemin
    private String root;
    private String currDirectory;
    // Variables de contrôle
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;
    //Variables de données
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private PrintWriter dataOutWriter;
    //Variables liées au serveur
    private int dataPort;
    private boolean connected = false;
    private userState userState;
    //Variables liées à la connexion au serveur
    //(celle-ci ne sont là que pour tester notre système, normalement il faudrait aller les chercher dans une vraie BDD)
    private ArrayList correctUsernames = new ArrayList<String>(Arrays.asList("anonymous", "charles"));
    private ArrayList correctPasswords = new ArrayList<String>(Arrays.asList("anonymous", "charles"));

    public FTPSession(Socket client, int dataPort)
    {
        super();
        this.controlSocket = client;
        this.dataPort = dataPort;
        this.currDirectory = System.getProperty("user.dir") + "/test";
        this.root = System.getProperty("user.dir");
    }

    private void execCommand(String text){
        String[] split = text.split(" ", 2);
        String command = split[0].toUpperCase(), arguments = split[1];

        debugPrint("Exec : " + command + " with [" + arguments + "]");

        // Traitement des différentes commandes avec leurs arguments
        switch(command){
            case "USER":
                execUSER(arguments);
                break;
            case "PASS":
                execPASS(arguments);
                break;
            case "CWD":
                execCWD(arguments);
                break;
            case "LIST":
                execLIST(arguments);
                break;
            case "PWD":
                execPWD(arguments);
                break;
            case "PASV":
                execPASV();
                break;
            case "SYST":
                execSYST();
                break;
            case "PORT":
                execPORT(arguments);
                break;
            case "RETR":
                execRETR(arguments);
                break;
            case "MKD":
                execMKD(arguments);
                break;
            case "RMD":
                execRMD(arguments);
                break;
            case "TYPE":
                execTYPE(arguments);
                break;
            case "STOR":
                execSTOR(arguments);
                break;
            case "QUIT":
                execQUIT(arguments);
                break;
            default:
                sendMessageToClient("501 Unknown command");
                debugPrint("Unhandled command : " + command);
                break;
        }
    }

    private void execUSER(String username) {
        if(correctUsernames.contains(username)){
            sendMessageToClient("331 Please specify the password");
        } else if (userState == userState.CONNECTED) {
            sendMessageToClient("530 Already logged in");
        } else {
            sendMessageToClient("530 Not logged in");
        }
    }

    private void execPASS(String password) {
        if (userState == userState.PASSWORDNEEDED && correctPasswords.contains(password)) {
            userState = userState.CONNECTED;
            sendMessageToClient("230 Login successful");
        } else if (userState == userState.CONNECTED) {
            sendMessageToClient("530 User already logged in");
        } else {
            sendMessageToClient("530 Not logged in");
        }
    }

    private void execCWD(String arguments) {
        String filename = currDirectory;

        // go one level up (cd ..)
        if (arguments.equals("..")) {
            int ind = filename.lastIndexOf("/");
            if (ind > 0)
            {
                filename = filename.substring(0, ind);
            }
        } else if ((arguments != null) && (!arguments.equals(".")))
        {
            filename = filename + "/" + arguments;
        }

        // check if file exists, is directory and is not above root directory
        File f = new File(filename);

        if (f.exists() && f.isDirectory() && (filename.length() >= root.length())) {
            currDirectory = filename;
            sendMessageToClient("250 The current directory has been changed to " + currDirectory);
        } else {
            sendMessageToClient("550 Failed to change directory");
        }
    }

    private void execLIST(String arguments) {
        if (dataConnection == null || dataConnection.isClosed())
        {
            sendMessageToClient("425 No data connection was established");
        }
        else
        {

            String[] dirContent = listingFiles(arguments);

            if (dirContent == null)
            {
                sendMessageToClient("550 File does not exist.");
            }
            else
            {
                sendMessageToClient("125 Opening ASCII mode data connection for file list.");

                for (int i = 0; i < dirContent.length; i++)
                {
                    sendMessageToClient(dirContent[i]);
                }

                sendMessageToClient("226 Transfer complete.");
                closeDataConnection();

            }

        }
    }

    private String[] listingFiles(String arguments) {
        // Construct the name of the directory to list.
        String filename = currDirectory;
        if (arguments != null) {
            filename = filename + "/" + arguments;
        }

        // Now get a File object, and see if the name we got exists and is a directory.
        File f = new File(filename);

        if (f.exists() && f.isDirectory()) {
            return f.list();
        }
        else if (f.exists() && f.isFile()) {
            String[] allFiles = new String[1];
            allFiles[0] = f.getName();
            return allFiles;
        } else {
            return null;
        }
    }

    private void execPWD(String arguments) {
        sendMessageToClient("257 \"" + currDirectory + "\"");
    }

    private void execPASV() {
    }

    private void execSYST() {
    }

    private void execPORT(String arguments) {
    }

    private void execRETR(String arguments) {
    }

    private void execMKD(String arguments) {
    }

    private void execRMD(String arguments) {
    }

    private void execTYPE(String arguments) {
    }

    private void execSTOR(String arguments) {
    }

    private void execQUIT(String arguments) {
    }

    private void sendMessageToClient(String message) {
        this.dataOutWriter.println(message);
    }

    private void sendDataToClient(String message) {
        if (dataConnection == null || dataConnection.isClosed()) {
            sendMessageToClient("425 No data connection was established");
            debugPrint("Cannot send data because no data connection was established");
        } else {
            dataOutWriter.print(message + '\r' + '\n');
        }
    }

    private void openDataConnectionPassive(int port) {
        try {
            dataSocket = new ServerSocket(port);
            dataConnection = dataSocket.accept();
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugPrint("Data connection (for passive mode) established");
        } catch (IOException e) {
            debugPrint("Could not create data connection.");
            e.printStackTrace();
        }
    }

    private void openDataConnectionActive(String ipAddress, int port) {
        try {
            dataConnection = new Socket(ipAddress, port);
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugPrint("Data connection (for active mode) established");
        } catch (IOException e) {
            debugPrint("Could not connect to client data socket");
            e.printStackTrace();
        }

    }

    private void closeDataConnection() {
        try {
            dataOutWriter.close();
            dataConnection.close();
            if (dataSocket != null) {
                dataSocket.close();
            }

            debugPrint("Data connection was closed");
        } catch (IOException e) {
            debugPrint("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataConnection = null;
        dataSocket = null;
    }

    private void debugPrint(String text) {
        if(this.debug) System.out.println("Session " + this.getId() + ": " + text);
    }

    public void run() {
        try {
            sendMessageToClient("220 Welcome to Java FTP-Server");
            while(connected){
                execCommand(controlIn.readLine());
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
