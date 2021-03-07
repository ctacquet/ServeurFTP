import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Classe pour se connecter au serveur FTP
 * Elle représente une session et est représenté en tant que Thread afin de pouvoir en exécuter plusieurs en même temps
 */
public class FTPSession extends Thread {
    /**
     * Debug pour afficher toutes les commandes exécutées
     */
    private final boolean debug = true;

    /**
     * Etat de l'utilisateur
     */
    private enum userState {NOTCONNECTED, PASSWORDNEEDED, CONNECTED}
    /**
     * Type de transfert de fichiers
     */
    private enum transferType {ASCII, BINARY}

    /**
     * Variables liées au chemin
     */
    private String root;
    private String currDirectory;
    private final String separator = "\\";

    /**
     * Variables permettant de transférer des messages avec le client
     */
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;

    /**
     * Variables de données pour le client
     */
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private PrintWriter dataOutWriter;

    /**
     * Variables liées au serveur
     */
    private int dataPort;
    private transferType transferMode = transferType.ASCII;
    private boolean connected = true;
    private userState userState;

    /**
     * Variables liées à la connexion au serveur
     * (celle-ci ne sont là que pour tester notre système, normalement il faudrait aller les chercher dans une vraie BDD)
     */
    private final ArrayList correctUsernames;
    private final ArrayList correctPasswords;

    /**
     * Constructeur qui crée une session sur un port défini (différent pour chaque utilisateur)
     * @param client Socket pour le client
     * @param dataPort Port du flux de données
     */
    public FTPSession(Socket client, int dataPort) {
        super();

        /**
         * Ajout d'utilisateurs et de leurs mots de passe
         *
         * Ici pour un genre de ProofOfConcept on ne se connecte pas à une base de données
         * mais dans le cas d'une application réelle il faudrait
         */
        this.correctUsernames = new ArrayList();
        this.correctUsernames.add("anonymous");
        this.correctUsernames.add("charles");
        this.correctPasswords = new ArrayList();
        this.correctPasswords.add("anonymous");
        this.correctPasswords.add("mdp");

        this.controlSocket = client;
        this.dataPort = dataPort;
        this.currDirectory = System.getProperty("user.dir") + separator + "test";
        this.root = System.getProperty("user.dir");
        this.userState = userState.NOTCONNECTED;
    }

    /**
     * Méthode permettant de rediriger chaque commande vers la bonne méthode
     * Elle nous permet également de séparer les commandes de leurs arguments
     * @param text Entrée du client FTP (commande avec ou sans argument)
     */
    private void execCommand(String text){
        //Cas où le flux de lecture de commande ne lit rien
        if(text == null){
            //debugPrint("execCommand - null command");
            return;
        }

        String[] split = text.split(" ", 2);
        String command = split[0].toUpperCase(), arguments = "";
        if(split.length > 1){
            arguments = split[1];
            debugPrint("Exec : " + command + " with [" + arguments + "]");
        } else debugPrint("Exec : " + command);

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
            case "NLST":
            case "LIST":
                execLIST(arguments);
                break;
            case "XPWD":
            case "PWD":
                execPWD();
                break;
            case "PASV":
                execPASV();
                break;
            case "SYST":
                execSYST();
                break;
            case "FEAT":
                execFEAT();
                break;
            case "OPTS":
                execOPTS();
                break;
            case "PORT":
                execPORT(arguments);
                break;
            case "RETR":
                execRETR(arguments);
                break;
            case "XMKD":
            case "MKD":
                execMKD(arguments);
                break;
            case "RMD":
            case "RM":
            case "DELE":
                execRM(arguments);
                break;
            case "TYPE":
                execTYPE(arguments);
                break;
            case "STOR":
                execSTOR(arguments);
                break;
            case "QUIT":
                execQUIT();
                break;
            default:
                sendMessageToClient("501 Unknown command");
                debugPrint("Unhandled command : " + command);
                break;
        }
    }

    /**
     * Méthode pour la commande USER
     * Permet à l'utilisateur de se connecter
     * @param username Nom d'utilisateur de l'utilisateur
     */
    private void execUSER(String username) {
        if(correctUsernames.contains(username)){
            userState = userState.PASSWORDNEEDED;
            sendMessageToClient("331 Please specify the password");
        } else if (userState == userState.CONNECTED) {
            sendMessageToClient("530 Already logged in");
        } else {
            sendMessageToClient("530 Not logged in - Bad username");
        }
    }

    /**
     * Méthode pour la commande PASS
     * Permet à l'utilisateur d'entrer son mot de passe
     * @param password Mot de passe de l'utilisateur
     */
    private void execPASS(String password) {
        if (userState == userState.PASSWORDNEEDED && correctPasswords.contains(password)) {
            userState = userState.CONNECTED;
            sendMessageToClient("230 Login successful");
        } else if (userState == userState.CONNECTED) {
            sendMessageToClient("530 User already logged in");
        } else {
            sendMessageToClient("530 Not logged in - Bad password");
        }
    }

    /**
     * Méthode pour la commande CWD / CD
     * Permet de se déplacer dans les dossiers
     * @param arguments Nom du dossier où l'on veut aller
     */
    private void execCWD(String arguments) {
        String filename = currDirectory;

        if (arguments.equals("..")) { //Dossier précédent
            int index = filename.lastIndexOf(separator);
            if (index > 0)
            {
                filename = filename.substring(0, index);
            }
        } else if ((arguments != null) && (!arguments.equals("."))) {
            filename = filename + separator + arguments;
        }

        File f = new File(filename);
        if (f.exists() && f.isDirectory() && (filename.length() >= root.length())) {
            currDirectory = filename;
            sendMessageToClient("250 The current directory has been changed to " + currDirectory);
        } else {
            sendMessageToClient("550 Failed to change directory");
        }
    }

    /**
     * Méthode pour la commande LIST / NLST
     * Permet de lister tous les fichiers et dossiers
     * @param arguments Dossier à analyser (si arguments est null alors on analyse le dossier actuel)
     */
    private void execLIST(String arguments) {
        if (dataConnection == null || dataConnection.isClosed()) {
            sendMessageToClient("425 No data connection was established");
        } else {
            String[] dirContent = listingFiles(arguments);

            if (dirContent == null) {
                sendMessageToClient("550 File does not exist.");
            } else {
                sendMessageToClient("125 Opening ASCII mode data connection for file list.");

                for (int i = 0; i < dirContent.length; i++) {
                    sendDataToClient(dirContent[i]);
                }

                sendMessageToClient("226 Transfer complete.");
                closeDataConnection();
            }
        }
    }

    /**
     * Méthode pour aider au listing des fichiers
     * (Utilisé à chaque appel de execLIST)
     * @param arguments Dossier à analyser (si arguments est null alors on analyse le dossier actuel)
     * @return Tableau de noms de fichiers et de dossiers
     */
    private String[] listingFiles(String arguments) {
        String filename = currDirectory;
        if (arguments != null) { //Cas d'un dossier différent du dossier actuel
            filename = filename + separator + arguments;
        }

        File f = new File(filename);
        if (f.exists() && f.isDirectory()) {
            return f.list();
        } else if (f.exists() && f.isFile()) {
            String[] allFiles = new String[1];
            allFiles[0] = f.getName();
            return allFiles;
        } else {
            return null;
        }
    }

    /**
     * Méthode pour la commande PWD
     * Nous dit dans quel dossier nous sommes
     */
    private void execPWD() {
        sendMessageToClient("257 \"" + currDirectory + "\"");
    }

    /**
     * Méthode pour la commande PASV
     * Passage en mode passif
     */
    private void execPASV() {
        String myIp = "127.0.0.1";
        String myIpSplit[] = myIp.split("\\.");

        int p1 = dataPort/256;
        int p2 = dataPort%256;

        sendMessageToClient("227 Entering Passive Mode ("+ myIpSplit[0] +"," + myIpSplit[1] + "," + myIpSplit[2] + "," + myIpSplit[3] + "," + p1 + "," + p2 +")");

        openDataConnectionPassive(dataPort);
    }

    /**
     * Méthode pour la commande SYST
     * Permet d'identifier le serveur
     */
    private void execSYST() {
        sendMessageToClient("215 Java FTP Server");
    }

    /**
     * Méthode pour la commande FEAT
     * Elle est nécessaire pour les clients FTP
     */
    private void execFEAT() {
        sendMessageToClient("211");
    }

    /**
     * Méthode pour la commande OPTS
     * Elle est nécessaire après la commande FEAT
     */
    private void execOPTS() {
        sendMessageToClient("200 OK");
    }

    /**
     * Méthode pour la commande PORT
     * Permet de créer une connexion au flux de données
     * @param arguments IP + PORT
     */
    private void execPORT(String arguments) {
        String[] stringSplit = arguments.split(",");
        String hostName = stringSplit[0] + "." + stringSplit[1] + "." + stringSplit[2] + "." + stringSplit[3];
        int port = Integer.parseInt(stringSplit[4])*256 + Integer.parseInt(stringSplit[5]);

        openDataConnectionActive(hostName, port);
        sendMessageToClient("200 Command OK");
    }

    /**
     * Méthode pour la commande RETR
     * Permet de récupérer une fichier depuis le serveur vers le client
     * @param file
     */
    private void execRETR(String file) {
        File f =  new File(currDirectory + separator + file);

        if(!f.exists()) {
            sendMessageToClient("550 File does not exist");
        } else {
            if (transferMode == transferType.BINARY) { // Binary mode
                BufferedOutputStream fout = null;
                BufferedInputStream fin = null;

                sendMessageToClient("150 Opening binary mode data connection for requested file " + f.getName());

                //Creation des flux de données
                try {
                    fout = new BufferedOutputStream(dataConnection.getOutputStream());
                    fin = new BufferedInputStream(new FileInputStream(f));
                }
                catch (Exception e) {
                    debugPrint("Could not create file streams");
                }

                debugPrint("Starting file transmission of " + f.getName());

                //Ecriture du fichier dans le flux
                byte[] buf = new byte[1024];
                int l = 0;
                try {
                    while ((l = fin.read(buf,0,1024)) != -1) {
                        fout.write(buf,0,l);
                    }
                } catch (IOException e) {
                    debugPrint("Could not read from or write to file streams");
                    e.printStackTrace();
                }

                //Fermeture des flux de données
                try {
                    fin.close();
                    fout.close();
                } catch (IOException e) {
                    debugPrint("Could not close file streams");
                    e.printStackTrace();
                }

                debugPrint("Completed file transmission of " + f.getName());

                sendMessageToClient("226 File transfer successful. Closing data connection.");
            } else { // ASCII mode
                sendMessageToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

                BufferedReader rin = null;
                PrintWriter rout = null;

                try {
                    rin = new BufferedReader(new FileReader(f));
                    rout = new PrintWriter(dataConnection.getOutputStream(),true);

                } catch (IOException e) {
                    debugPrint("Could not create file streams");
                }

                String s;
                try {
                    while((s = rin.readLine()) != null) {
                        rout.println(s);
                    }
                } catch (IOException e) {
                    debugPrint("Could not read from or write to file streams");
                    e.printStackTrace();
                }

                try {
                    rout.close();
                    rin.close();
                } catch (IOException e) {
                    debugPrint("Could not close file streams");
                    e.printStackTrace();
                }
                sendMessageToClient("226 File transfer successful. Closing data connection.");
            }
        }
        closeDataConnection();
    }

    /**
     * Méthode pour la commande MKD
     * Permet de créer un dossier
     * @param arguments Nom du dossier
     */
    private void execMKD(String arguments) {
        if (arguments != null && arguments.matches("^[a-zA-Z0-9]+$")) {
            File dir = new File(currDirectory + separator + arguments);

            if(!dir.mkdir()) {
                sendMessageToClient("550 Failed to create new directory");
                debugPrint("Failed to create new directory");
            } else {
                sendMessageToClient("250 Directory successfully created");
            }
        } else {
            sendMessageToClient("550 Invalid name");
        }
    }

    /**
     * Méthode pour la commande RM
     * Permet de supprimer un fichier ou dossier vide
     * @param file Fichier ou dossier à supprimer
     */
    private void execRM(String file) {
        String filename = currDirectory;
        if (file != null && file.matches("^[a-zA-Z0-9]+$")) {
            filename = filename + separator + file;

            File f = new File(filename);
            if (f.exists()) {
                if(f.isDirectory()){
                    if(f.delete()) sendMessageToClient("250 Directory was successfully removed");
                    else sendMessageToClient("550 Requested action not taken. Directory not empty");
                } else {
                    if (f.delete()) sendMessageToClient("250 File was successfully removed");
                    else sendMessageToClient("550 Requested action not taken. File not found");
                }
            } else {
                sendMessageToClient("550 Requested action not taken. File not found");
            }
        } else {
            sendMessageToClient("550 Invalid file name");
        }
    }

    /**
     * Méthode pour la commande TYPE
     * Permet de changer le mode de transfert (ASCII OU BINARY)
     * @param mode Mode de transfert ("A" pour ASCII et "I" pour image/BINARY)
     */
    private void execTYPE(String mode) {
        if(mode.toUpperCase().equals("A")) {
            transferMode = transferType.ASCII;
            sendMessageToClient("200 OK");
        } else if(mode.toUpperCase().equals("I")) {
            transferMode = transferType.BINARY;
            sendMessageToClient("200 OK");
        } else sendMessageToClient("504 Not OK");
    }

    /**
     * Méthode pour la commande STOR
     * Permet d'envoyer un fichier depuis le client vers le serveur
     * @param file Nom du fichier à envoyer
     */
    private void execSTOR(String file) {
        if (file == null) {
            sendMessageToClient("501 No filename given");
        } else {
            File f =  new File(currDirectory + separator + file);

            if(f.exists()) {
                sendMessageToClient("550 File already exists");
            } else {
                if (transferMode == transferType.BINARY) { // Binary mode
                    BufferedOutputStream fout = null;
                    BufferedInputStream fin = null;

                    sendMessageToClient("150 Opening binary mode data connection for requested file " + f.getName());

                    try {
                        //Creation des flux de données
                        fout = new BufferedOutputStream(new FileOutputStream(f));
                        fin = new BufferedInputStream(dataConnection.getInputStream());
                    } catch (Exception e) {
                        debugPrint("Could not create file streams");
                    }

                    debugPrint("Start receiving file " + f.getName());

                    byte[] buf = new byte[1024];
                    int l = 0;
                    try {
                        while ((l = fin.read(buf,0,1024)) != -1) {
                            fout.write(buf,0,l);
                        }
                    } catch (IOException e) {
                        debugPrint("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    //Fermeture des flux de données
                    try {
                        fin.close();
                        fout.close();
                    } catch (IOException e) {
                        debugPrint("Could not close file streams");
                        e.printStackTrace();
                    }

                    debugPrint("Completed receiving file " + f.getName());
                    sendMessageToClient("226 File transfer successful. Closing data connection.");
                } else { // ASCII mode
                    sendMessageToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

                    BufferedReader rin = null;
                    PrintWriter rout = null;

                    try {
                        rin = new BufferedReader(new InputStreamReader(dataConnection.getInputStream()));
                        rout = new PrintWriter(new FileOutputStream(f),true);

                    } catch (IOException e) {
                        debugPrint("Could not create file streams");
                    }

                    String s;
                    try {
                        while((s = rin.readLine()) != null)
                        {
                            rout.println(s);
                        }
                    } catch (IOException e) {
                        debugPrint("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    try {
                        rout.close();
                        rin.close();
                    } catch (IOException e) {
                        debugPrint("Could not close file streams");
                        e.printStackTrace();
                    }
                    sendMessageToClient("226 File transfer successful. Closing data connection.");
                }
            }
            closeDataConnection();
        }
    }

    /**
     * Méthode pour la commande QUIT
     * Permet au client de se déconnecter
     * Elle va arrêter la boucle du run()
     */
    private void execQUIT() {
        sendMessageToClient("221 Closing connection");
        connected = false;
    }

    /**
     * Méthode pour envoyer un message au client
     * @param message Message à envoyer
     */
    private void sendMessageToClient(String message) {
        this.controlOutWriter.println(message);
    }

    /**
     * Méthode pour envoyer des données au client
     * @param message Message à envoyer
     */
    private void sendDataToClient(String message) {
        if (dataConnection == null || dataConnection.isClosed()) {
            sendMessageToClient("425 No data connection was established");
            debugPrint("Cannot send data because no data connection was established");
        } else {
            dataOutWriter.print(message + '\r' + '\n');
        }
    }

    /**
     * Méthode obligatoire pour pouvoir envoyer des données en mode passif
     * Elle va changer nos flux de données pour permettre des transferts
     * @param port Port sur lequel on ouvre le flux
     */
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


    /**
     * Méthode obligatoire pour pouvoir envoyer des données en mode actif
     * Elle va changer nos flux de données pour permettre des transferts
     * @param port Port sur lequel on ouvre le flux
     */
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

    /**
     * Méthode qui permet de fermer les flux précédemment créés
     */
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

    /**
     * Methode de debug afin de nous afficher ce qui est exécuté
     * @param text Texte à afficher
     */
    private void debugPrint(String text) {
        if(debug) System.out.println("Session " + this.getId() + ": " + text);
    }

    /**
     * Methode run() obligatoire pour chaque Thread
     */
    public void run() {
        try {
            // Input from client
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

            // Output to client, automatically flushed after each print
            controlOutWriter = new PrintWriter(controlSocket.getOutputStream(), true);

            sendMessageToClient("220 Welcome to Java FTP-Server");
            while(connected) {
                execCommand(controlIn.readLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            controlIn.close();
            controlOutWriter.close();
            controlSocket.close();
            debugPrint("Sockets closed and session has stopped");
        } catch(IOException e) {
            e.printStackTrace();
            debugPrint("Problem appearing while closing sockets");
        }
    }
}
