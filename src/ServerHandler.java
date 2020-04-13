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
import java.util.*;

public class ServerHandler implements Runnable{


    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ClientNode node;
    private final Object credLock = new Object();
    private final Object clientLock = new Object();
    private final Object graph_lock = new Object();      
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
                    accept_deny_block(req);
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
        Response res;
        Credentials cred;
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

    private static final String userExists = "Client id already exists.";

    private void register(Request request) throws IOException {
        Credentials credentials;
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
            createPath(Paths.get(credPath));
            File credIndex = new File(credPath);

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

    private void write_register(Credentials credentials){

        try{
            FileWriter fw = new FileWriter(credPath,true);
            BufferedWriter br = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(br);
            synchronized (credLock) {
                pw.println(credentials.getClientID() + " " + credentials.getPassword());
                pw.flush();
            }
            //close streams
            pw.close();
            br.close();
            fw.close();
        }catch (Exception e){
            System.err.println("Something went wrong creating new record in "+credPath);
        }

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
        System.out.println("aha");
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
        System.out.println("aha");
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
        System.out.println("aha");
    }

    private void getFeed(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        System.out.println("aha");
    }

    private void getUserFeed(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        System.out.println("aha");
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
        out.writeObject(new Response(TagTypes.SERVER, ResponseType.OK, BodyType.MAP_STRING_STRING, client_requests));
        out.flush();
    }

    //FORMAT: "NAME" follow:"(y|yes)", "(n|no)",null,(block|b) directory:"(y|yes)", "(n|no)", null, (block|b)
    private void accept_deny_block(Request request) throws IOException{
        if(!authenticate(request.getCookie())){
            out.writeObject(bad_req);
            out.flush();
            return;
        }
        String getLine = (String) request.getBody();
        String[] parts = getLine.split("\\s");
        String client = parts[0];
        String sender = request.getCookie().getClientID();
        int follow = parse_option(parts[1]);
        boolean fol_result = switchRequest(sender, client, follow, RequestType.FOLLOW);
        int directory = -1;
        boolean dir_request = false;
        if(follow != 2 && parts.length == 3){
            directory = parse_option(parts[2]);
            dir_request = switchRequest(sender, client, directory, RequestType.DIRECTORY);
        }
        String result = fol_result ? req_format(follow, " following ",client," from "):
                "Follow query failed";
        if(parts.length == 3){
            result += (dir_request) ? req_format(directory, " directory access ",client,"to"):
                "Directory query failed";
        }
        out.writeObject(new Response(TagTypes.SERVER, (result.equals("")? ResponseType.INTERNAL_ERROR: ResponseType.OK),
                BodyType.OUTPUT, result));
        out.flush();
    }

    private String req_format(int choice, String request, String client, String adj){
        switch (choice){
            case 0:
                return "Accepted " + request + adj +client;
            case 1:
                return "Refused " + request + adj + client;
            case 2:
                return "Blocked from " + request + client;
            case 10:
                return "Accepted and followed back " + adj + client;
            default:
                return "";
        }
    }

    private boolean switchRequest(String client, String requester, int choice, RequestType req){
        switch (choice){
            case 0: //accept
            case 10:
                return acceptRequest(client, requester, req, choice);
            case 1: //deny
                return denyRequest(client, requester);
            case 2:
                return block(client, requester, req);
            default:
                return true;
        }
    }

    private boolean denyRequest(String client,String requester){
        try{
            String path = requestPath[0]+"/"+client+"/"+requestPath[1];
            createPath(Paths.get(path));
            File file = new File(path);
            // Read requests
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            LinkedHashMap<String, String> request_names = new LinkedHashMap<>();
            String readline;
            while((readline = br.readLine()) != null) {
                String client_id = readline.split("\\s")[0];
                request_names.put(client_id, readline);
            }
            // Close read stream
            br.close();
            fr.close();
            // Edit file or remove row if everything is null
            if(request_names.get(requester) !=null){
                new Thread(new Notification(client, requester, "denied your following")).start();
                request_names.remove(requester);
            }
            // Write the new map
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            request_names.forEach( (k, v)->{
                pw.println(v);
                pw.flush();
            });
            // Close write stream
            pw.close();
            bw.close();
            fw.close();
        }catch (IOException e){ return false;}
        return true;
    }

    private boolean acceptRequest(String client,String requester,RequestType req, int choice){
        try{
            String path = requestPath[0]+"/"+client+"/"+requestPath[1];
            createPath(Paths.get(path));
            File file = new File(path);
            // Read requests
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            LinkedHashMap<String, String> request_names = new LinkedHashMap<>();
            String readLine;
            while((readLine = br.readLine()) != null) {
                String client_id = readLine.split("\\s")[0];
                request_names.put(client_id, readLine);
            }
            // Close read stream
            br.close();
            fr.close();
            // Edit file or remove row if everything is null
            if(request_names.get(requester) !=null){
                if(req == RequestType.FOLLOW){
                    new Thread(new GraphHandler(client, requester, "add")).start();
                    if(choice == 10){
                        new Thread(new Notification(client, requester, "accepted your following and followed back")).start();
                        new Thread(new GraphHandler(requester, client, "add")).start();
                    }else{
                        new Thread(new Notification(client, requester, "accepted your following")).start();
                    }
                }else if(req == RequestType.DIRECTORY){
                    new Thread(new Notification(client, requester, "allowed you to access the directory.")).start();
                    addToDirectoryRules(client, requester);
                }
                String[] parts = request_names.get(requester).split("\\s");
                parts[1] = req == RequestType.FOLLOW? "null": parts[1];
                parts[2] = req == RequestType.DIRECTORY? "null": parts[2];
                if(parts[1].equals(parts[2]) && parts[1].equals("null")){
                    request_names.remove(requester);
                }else{
                    request_names.put(requester, String.join(" ", parts));
                }
            }
            // Write the new map
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            request_names.forEach( (k, v)->{
                pw.println(v);
                pw.flush();
            });
            // Close write stream
            pw.close();
            bw.close();
            fw.close();
        }catch (IOException e){ return false;}
        return true;
    }

    private boolean block(String client,String requester,RequestType req){
        try{
            String path = requestPath[0]+"/"+client+"/"+requestPath[2];
            createPath(Paths.get(path));
            File file = new File(path);
            // Read blocked names
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            LinkedHashSet<String> blocked_names = new LinkedHashSet<>();
            String readline;
            while((readline = br.readLine()) != null) blocked_names.add(readline);
            // Close read stream
            br.close();
            fr.close();
            // add new blocked client
            blocked_names.add(requester);
            // Write the new set of names
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            for (String blocked_name : blocked_names) {
                pw.println(blocked_name);
                pw.flush();
            }
            // Close write stream
            pw.close();
            bw.close();
            fw.close();
        }catch (IOException e){ return false;}
        return true;
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
        return db_hash_session.equals(session.getHash());
    }

    String[] requestPath = new String[]{"./database/Client/", "requests.txt", "blocked.txt", "directory.txt"};

    private boolean writeRequest(String follower, String followed, RequestType req_type){

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
            String path = requestPath[0]+"/"+followed+"/"+requestPath[1];
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
        }catch (IOException ignored){}
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
        if(option.equals("b")) return 2;
        if(option.equals("f")) return 10;
        return -1;
    }

    public class Notification implements Runnable{

        private final String client;
        private final String requester;
        private final String message;

        public Notification(String client, String requester, String message){
            this.client = client;
            this.requester = requester;
            this.message = message;
        }

        public void run(){
            String path = requestPath[0]+requester+"/"+"notifications.txt";
            String notification = client + message;
            createPath(Paths.get(path));
            try{
                File file = new File(path);
                FileWriter fw = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.append(notification);
                bw.flush();
                // Close streams
                bw.close();
                fw.close();
            }catch(IOException ignore){ }
        }

    }

    private boolean addToDirectoryRules(String client,String requester){
        try{
            String path = requestPath[0]+"/"+client+"/"+requestPath[3];
            createPath(Paths.get(path));
            File file = new File(path);
            // Read blocked names
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            LinkedHashSet<String> blocked_names = new LinkedHashSet<>();
            String readline;
            while((readline = br.readLine()) != null) blocked_names.add(readline);
            // Close read stream
            br.close();
            fr.close();
            // add new blocked client
            blocked_names.add(requester);
            // Write the new set of names
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            for (String blocked_name : blocked_names) {
                pw.println(blocked_name);
                pw.flush();
            }
            // Close write stream
            pw.close();
            bw.close();
            fw.close();
        }catch (IOException e){ return false;}
        return true;
    }

    class GraphHandler implements Runnable{

        private final String client;
        private final String follower;
        private final String add_remove;

        GraphHandler(String client, String follower, String add_remove){
            this.client = client;
            this.follower = follower;
            this.add_remove = add_remove;
        }

        public void run(){
            String path = "./database/SocialGraph.txt";
            createPath(Paths.get(path));
            try {
                synchronized (graph_lock){
                    File file = new File(path);
                    FileReader fr = new FileReader(file);
                    BufferedReader br = new BufferedReader(fr);
                    LinkedHashMap<String, String> rows = new LinkedHashMap<>();
                    String readline;
                    // Read file and put every client and their followers to the hash map
                    while((readline = br.readLine()) != null) {
                        if(readline.isBlank()) continue;
                        String client_id = readline.split("\\s")[0];
                        String remainder = readline.substring((client_id+".").length());
                        rows.put(client_id, remainder);
                    }
                    // User exists in the social graph (he has followers)
                    if(rows.get(client) != null){
                        // Get client's row and add the follower in the set
                        HashSet<String> followers = new HashSet<>(Arrays.asList(rows.get(client).split("\\s")));
                        if(add_remove.equals("add")) followers.add(follower);
                        if(add_remove.equals("remove")) followers.remove(follower);
                        StringBuilder sb = new StringBuilder();
                        followers.forEach((follower)-> sb.append(follower).append(" "));
                        rows.put(client, sb.toString());
                    }
                    // else put him as new
                    else{
                        if(add_remove.equals("remove")) return;
                        rows.put(client, follower);
                    }
                    // Write the new HashMap to the SocialGraph.txt
                    FileWriter fw = new FileWriter(file);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter pw = new PrintWriter(bw);
                    rows.forEach((k, v)->{
                        pw.println(k+" "+v);
                        pw.flush();
                    });
                    // Close write streams
                    pw.close();
                    bw.close();
                    fw.close();
                    // Close read streams
                    br.close();
                    fr.close();
                }
            }catch (IOException ignored){ ignored.printStackTrace();}
        }

    }

}
