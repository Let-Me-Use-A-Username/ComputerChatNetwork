import com.network.ClientNode;
import com.objects.*;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerHandler implements Runnable{


    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ClientNode node;
    private final Object credLock = new Object();
    private final Object clientLock = new Object();
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
            Request req = (Request) in.readObject();
            switch (req.getRequest()){
                case REGISTER:
                    register(req);
                    break;
                case LOG_IN:
                    logIn(req);
                    break;
                    //TODO: post(5)
                case POST:
                    post(req);
                    break;
                case FOLLOW:
                    follow(req);
                    break;
                    //TODO: unfollow (3)
                case UNFOLLOW:
                    unfollow(req);
                    break;
                case GETALL:
                    getAll(req);
                    break;
                case SEARCH:
                    search(req);
                    break;
                case GETFEED:
                    getFeed(req);
                    break;
                case GET_USER_FEED:
                    getUserFeed(req);
                    break;
                    //TODO: check requests (4)
                case CHECK_REQUESTS:
                    checkRequest(req);
                    break;
                case ACCEPT_DENY:
                    accept_deny(req);
                    break;
                default:
                    noQuery();
                    break;
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
            Session session = new Session(hash(cred.getPassword()), cred.getClientID());
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
            return;
        }
        write_register(credentials);
        out.writeObject(new Response(TagTypes.SERVER, ResponseType.CREATED,BodyType.OUTPUT,
                "User "+credentials.getClientID()+" created"));
        out.flush();
    }

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

    Response bad_req = new Response(TagTypes.SERVER, ResponseType.BAD_REQUEST, BodyType.OUTPUT, "Bad Request: session");

    //TODO: post(5)
    private void post(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
    }

    private void follow(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        String follower = request.getCookie().getClientID();
        String toFollow = (String) request.getBody();
        if(!userExists(toFollow, null)){
            out.writeObject(new Response(TagTypes.SERVER,ResponseType.NOT_FOUND, BodyType.OUTPUT, user_404));
            out.flush();
            return;
        }
        //TODO: Check if user is blocked
        if(writeRequest(follower, toFollow, RequestType.FOLLOW)){
            out.writeObject(new Response(TagTypes.SERVER, ResponseType.CREATED, BodyType.OUTPUT, "Request sent!"));
        }else{
            out.writeObject(new Response(TagTypes.SERVER, ResponseType.INTERNAL_ERROR, BodyType.OUTPUT, "Failed to sent request!"));
        }
        out.flush();
    }

    //TODO: unfollow (3)
    private void unfollow(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
    }

    private void getAll(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        HashSet<String> users = readAllUsers();
        Response res = new Response(TagTypes.SERVER,ResponseType.OK, BodyType.STRING_SET, users);
        out.writeObject(res);
        out.flush();
    }

    private void search(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
    }

    private void getFeed(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
    }

    private void getUserFeed(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
    }

    //TODO: check requests (4)
    private void checkRequest(Request request) throws IOException {
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        LinkedHashMap<String, String> client_requests = getRequests(request.getCookie().getClientID());
        // No requests
        if(client_requests==null){
            out.writeObject(new Response(TagTypes.SERVER,ResponseType.OK, BodyType.OUTPUT, "No requests."));
            out.flush();
            return;
        }
        out.writeObject(new Response(TagTypes.SERVER, ResponseType.OK, BodyType.MAP_STRING_STRING, (Map<String, String>) client_requests));
        out.flush();
    }

    //FORMAT: "NAME" follow:"(y|yes)", "(n|no)" or null directory:"(y|yes)", "(n|no)" or null

    private void accept_deny(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        String getLine = (String) request.getBody();
        String[] parts = getLine.split("\\s");
        String client = parts[0];
        int follow = parse_option(parts[1]);
        int directory = parse_option(parts[2]);
    }

    private static final Response noQueryRes =
            new Response(TagTypes.SERVER, ResponseType.BAD_REQUEST, BodyType.OUTPUT, "Bad request: No such request.");

    private void noQuery() throws IOException {
        out.writeObject(noQueryRes);
        out.flush();
    }


    private boolean authenticate(Session session){
        if(session == null) return false;
        if(session.getClientID() == null || session.getHash() == null) return false;
        String db_hash_session = server.sessions.get(session.getClientID());
        if(db_hash_session == null) return false;
        if(!db_hash_session.equals(session.getHash())) return false;
        return true;
    }



    String[] requestPath = new String[]{"./database/Client/", "requests.txt"};

    private boolean writeRequest(String follower, String followee, RequestType req_type){

        String[] parts = new String[]{follower, "null", "null"};

        switch (req_type){
            case FOLLOW:
                parts[1] = "follow";
                break;
            case DIRECTORY:
                parts[2] = "directory";
                break;
            default:
                break;
        }

        try{
            String path = requestPath[0]+"/"+followee+"/"+requestPath[1];
            createPath(Paths.get(path));
            File file = new File(path);
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            Map<String, String> fileMap = new LinkedHashMap<>();
            String readline;
            while((readline = br.readLine()) != null){
                String client_id = readline.split("\\s")[0];
                fileMap.put(client_id, readline);
            }
            br.close();
            fr.close();
            String update = fileMap.get(follower);
            if(update!=null){
                String[] row = update.split("\\s");
                if(req_type == RequestType.FOLLOW){
                    parts[2] = row[2];
                }else{
                    parts[1] = row[1];
                }
            }
            String newRow = String.join(" ", parts);
            fileMap.put(follower, newRow);
            //Create new file
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            //lock Client folder to write
            synchronized (clientLock){
                fileMap.forEach((k, v)->{
                    pw.println(v);
                    pw.flush();
                });
            }
            //Close streams
            pw.close();
            bw.close();
            fw.close();
        }catch(IOException e){
            e.printStackTrace();
        }
        return true;
    }

    private LinkedHashMap<String, String> getRequests(String client_id){
        LinkedHashMap<String, String> req_map = new LinkedHashMap<>();
        try{
            File file = new File(requestPath[0]+client_id+"/"+requestPath[1]);
            if(!file.exists()) return null;
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String readline;
            synchronized (clientLock){
                while((readline = br.readLine()) != null){
                    String client = readline.split("\\s")[0];
                    req_map.put(client, readline);
                }
            }
            br.close();
            fr.close();
        }catch (IOException e){}
        if(req_map.size() == 0 ) return null;
        return req_map;
    }

    private HashSet<String> readAllUsers(){
        HashSet<String> userSet = new HashSet<>();
        try{
            File file = new File(credPath);
            if(!file.exists()) return userSet;
            FileReader fr = new FileReader(file.getPath());
            BufferedReader br = new BufferedReader(fr);
            String readline;
            synchronized (credLock){
                while((readline = br.readLine()) != null){
                    String client_id = readline.split("\\s+")[0];
                    userSet.add(client_id);
                }
            }
            br.close();
            fr.close();
        }catch(IOException ignore){}
        return userSet;
    }

    private void createPath(Path path){
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                if (!Files.exists(path))
                    Files.createFile(path);
                Files.write(path, ("").getBytes());
            }
        }catch (IOException ignore){}
    }

    private int parse_option(String option){
        if(option.equals("null")) return -1;
        option = option.toLowerCase().substring(0, 1);
        if(option.equals("y")) return 0;
        if(option.equals("n")) return 1;
        return -1;
    }

}
