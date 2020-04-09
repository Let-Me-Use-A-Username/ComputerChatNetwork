import com.network.ClientNode;
import com.objects.*;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ServerHandler implements Runnable{


    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ClientNode node;
    private final Object credLock = new Object();
    private final Server server;

    public ServerHandler(Socket socket, Server server){
        this.socket = socket;
        this.server = server;
        node = new ClientNode(socket.getInetAddress().getHostName(), socket.getPort());
    }

    public void run(){
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Request req = (Request) in.readObject();
                switch (req.getRequest()){
                    case REGISTER:
                        register(req);
                        break;
                    case LOG_IN:
                        logIn(req);
                        break;
                    case REQUEST:
                        break;
                    default:
                        break;
                }
            }
        }catch(EOFException ignore){
            System.out.println("Client "+node.toString()+" closed connection.");
        } catch (IOException | ClassNotFoundException e){
            System.err.println("Something went wrong during connection.");
            e.printStackTrace();
        }finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            }catch (IOException e){
                System.err.println("Failed to close the Handler's streams.");
            }
        }
    }

    private void checkFirstInput() throws IOException{

    }

    /*
     *
     * Log in methods
     *
     */
    private static final String no_cred = "Provide credentials";
    private static final String user_404 = "User not found";
    private static final String welcome = "Welcome client ";
    private static final String bad_cred = "Wrong credentials for client ";

    private void logIn(Request request) throws IOException {
        Response res = null;
        Credentials cred = null;
        Credentials userCred = new Credentials("", "");
        if(!request.getBodyType().equals(BodyType.CREDENTIAL)){
            bad_credentials();
            return;
        }
        try{
            cred = (Credentials) request.getBody();
        }catch(Exception e){
            bad_credentials();
            return;
        }
        if(!userExists(cred.getClientID(), userCred)){
            user404();
            return;
        }
        if(cred.getPassword().equals(userCred.getPassword())){
            Session session = new Session(hash(cred.getPassword()));
            server.sessions.put(cred.getClientID(), session.getHash());
            res = new Response(TagTypes.SERVER,ResponseType.OK, BodyType.OUTPUT,
                    welcome+cred.getClientID(), session);
            out.writeObject(res);
            out.flush();
        }
        bad_credentials(cred.getClientID());
    }

    private  void bad_credentials() throws IOException{
        Response res = new Response(TagTypes.SERVER,ResponseType.BAD_REQUEST,BodyType.OUTPUT,no_cred);
        out.writeObject(res);
        out.flush();
    }

    private void user404() throws IOException{
        Response res = new Response(TagTypes.SERVER,ResponseType.NOT_FOUND,BodyType.OUTPUT,user_404);
        out.writeObject(res);
        out.flush();
    }

    private void bad_credentials(String clientID) throws IOException{
        Response res = new Response(TagTypes.SERVER,ResponseType.BAD_REQUEST,BodyType.OUTPUT,bad_cred+clientID);
        out.writeObject(res);
        out.flush();
    }

    /*
     *
     * Register methods
     *
     */

    private static final String registerOut = "Please give me your credentials, to register";
    private static final String userExists = "Client id already exists.";

    private void log(String msg){
        System.out.println(msg);
    }

    private void register(Request request) throws IOException {
        Credentials credentials = null;
        if(!request.getBodyType().equals(BodyType.CREDENTIAL)){
            bad_credentials();
            return;
        }
        try{
            credentials = (Credentials) request.getBody();
        }catch(Exception e){
            bad_credentials();
            return;
        }
        boolean exists = userExists(credentials.getClientID(), null);
        if(exists){
            out.writeObject(new Response(TagTypes.SERVER, ResponseType.BAD_REQUEST, BodyType.OUTPUT, userExists));
            out.flush();
            log("User existed");
            return;
        }
        log("User didn't exist");
        write_register(credentials);
        out.writeObject(new Response(TagTypes.SERVER, ResponseType.CREATED,BodyType.OUTPUT,
                "User "+credentials.getClientID()+" created"));
        out.flush();
    }

//    try{
//        Files.createDirectory(Paths.get("brokerFiles/"+serverInfo.getAddress()+"_"+serverInfo.getPort()));
//    }catch(FileAlreadyExistsException ignored) {}
//
//    File temp = new File("brokerFiles/" + serverInfo.getAddress() + "_" + serverInfo.getPort() + "/" + artistSong + ".mp3");
//
//    OutputStream os = new FileOutputStream(temp, true);
//
//        for (MusicValue songFile : songFiles) os.write(songFile.getChunk());
//
//        os.close();

    private final String credPath = "./database/ClientCredentials/index.txt";
    private boolean userExists(String clientID, Credentials credentials){
        boolean found = false;
        try{
            File credIndex = new File(credPath);
            // Check if file exists
            if(!credIndex.exists()){
                credIndex.createNewFile();
                return false;
            }
            // Check all records
            FileReader fr = new FileReader(credIndex.getPath());
            BufferedReader br = new BufferedReader(fr);
            String readLine;
            synchronized (credLock) {
                while (true) {
                    readLine = br.readLine();
                    if (readLine == null) break;
                    String[] parts = readLine.split("\\s+");
                    if (parts[0].equals(clientID)) {
                        found = true;
                        if (!(credentials == null)) {
                            credentials.setClientID(parts[0]);
                            credentials.setPassword(parts[1]);
                        }
                        break;
                    }
                }
            }
            br.close();
            fr.close();
        }catch(Exception e){
            System.err.println("Something went wrong scanning folder "+credPath);
        }
        // Return result
        return found;
    }

    private boolean write_register(Credentials credentials){
        boolean wroteOnFile = false;
        try{
            FileWriter fw = new FileWriter(credPath,true);
            BufferedWriter br = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(br);
            synchronized (credLock) {
                pw.println(credentials.getClientID() + " " + credentials.getPassword());
                pw.flush();
            }
            wroteOnFile = true;
            //close streams
            pw.close();
            br.close();
            fw.close();
        }catch (Exception e){
            System.err.println("Something went wrong creating new record in "+credPath);
        }
        return wroteOnFile;
    }

    private String hash(String password){
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(password.getBytes());
            BigInteger sigNum = new BigInteger(1,messageDigest);
            String hash = sigNum.toString(16);
            while(hash.length()<32) hash = "0" + hash;
            return hash;

        }catch(NoSuchAlgorithmException e){
            return null;
        }
    }

}
