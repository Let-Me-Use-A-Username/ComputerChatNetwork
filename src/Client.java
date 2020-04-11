import com.objects.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client implements  Runnable{

    private final NodeReader reader = new NodeReader();
    private final ServerInfo server;
    private final String config;
    private Scanner scanner;

    private Session session;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public Client(String config){
        this.config = config;
        server = reader.readServerInfo(config);
    }

    private void printMenu(){
        System.out.println("1. Register");
        System.out.println("2. Log in");
        System.out.println("3. Search photograph");
        System.out.println("4. Post");
        System.out.println("5. See all registered clients");
        System.out.println("6. Follow client");
        System.out.println("7. Unfollow client");
        System.out.println("8. Accept/Deny follow request (if any)");
        System.out.println("9. Refresh");
        System.out.println("10. Check client activity");
        System.out.println("0. Exit");
    }

    private boolean connect(ServerInfo serverInfo){
        try{
            socket = new Socket(serverInfo.getAddress(), serverInfo.getPort());
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            return true;
        }catch(IOException e){
            System.out.println("Failed to connect to "+serverInfo.toString());
        }
        return false;
    }

    private void disconnect(Socket socket){
        try{
            socket.close();
        }catch(IOException ignore){}
    }


    public void run(){
        scanner = new Scanner(System.in);
        String input;
        int choice = -1;
        do{
            printMenu();
            input = scanner.nextLine();
            try{
               choice = Integer.parseInt(input);
            }catch (Exception ignore){}
            try{
                switch (choice){
                    case 1:
                        register();
                        break;
                    case 2:
                        login();
                        break;
                    case 3:
                        searchPhoto();
                        break;
                    case 4:
                        post();
                        break;
                    case 5:
                        seeAll();
                        break;
                    case 6:
                        follow();
                        break;
                    case 7:
                        unfollow();
                        break;
                    case 8:
                        followRequests();
                        break;
                    case 9:
                        refreshFeed();
                        break;
                    case 10:
                        checkClient();
                        break;
                    default:
                        break;
                }
            }catch (IOException | ClassNotFoundException e){
                System.err.println("Something went wrong during connection.");
            }
        }while (!input.equals("0"));
        System.out.println("Closing client...");
    }

    //Register
    private void register() throws IOException, ClassNotFoundException {
        resetFeedIndex();
        // Read user credentials
        Credentials credentials = credential_input();
        if(credentials == null) return;
        // Send register request
        Request request = new Request(TagTypes.CLIENT,null, RequestType.REGISTER, BodyType.CREDENTIAL,credentials);
        if(!connect(server)) return;
        send(request);
        // Get Response
        Response response = (Response) in.readObject();
        if(response.getBodyType().equals(BodyType.OUTPUT)) output((String)response.getBody());
        disconnect(socket);
    }

    //Log In
    private void login() throws IOException, ClassNotFoundException {
        resetFeedIndex();
        // Read user credentials
        Credentials credentials = credential_input();
        if(credentials == null) return;
        // Send login request
        Request request = new Request(TagTypes.CLIENT,null, RequestType.LOG_IN, BodyType.CREDENTIAL,credentials);
        if(!connect(server)) return;
        send(request);
        // Get Response
        Response response = (Response) in.readObject();
        session = response.getSession();
        if(response.getBodyType().equals(BodyType.OUTPUT)) output((String)response.getBody());
        disconnect(socket);
    }

    //Search Photo
    private void searchPhoto() {
        resetFeedIndex();
    }

    //Post photo
    private void post() {
        resetFeedIndex();
    }

    //See all clients
    private void seeAll() throws IOException, ClassNotFoundException{
        resetFeedIndex();
        Request request = new Request(TagTypes.CLIENT,session, RequestType.GETALL, "", "");
        sendRequest(request);
    }

    //Follow a user
    private void follow() throws IOException, ClassNotFoundException{
        resetFeedIndex();
        System.out.println("Enter client you wish to follow");
        String client_id = scanner.nextLine();
        Request request = new Request(TagTypes.CLIENT,session, RequestType.FOLLOW, BodyType.CLIENT_ID, client_id);
        sendRequest(request);
    }

    //Unfollow a user
    private void unfollow() throws IOException, ClassNotFoundException{
        resetFeedIndex();
        System.out.println("Enter client you wish to unfollow");
        String client_id = scanner.nextLine();
        Request request = new Request(TagTypes.CLIENT,session, RequestType.UNFOLLOW, BodyType.CLIENT_ID, client_id);
        sendRequest(request);
    }

    // Check requests from users who requested to follow you
    // and accept them or deny them
    private void followRequests() throws IOException, ClassNotFoundException{
        resetFeedIndex();
        Request request = new Request(TagTypes.CLIENT,session, RequestType.CHECK_REQUESTS, "", "");
        Response response = sendRequest(request);
        if(response==null) return;
        //while(session!=null){

        //}
        return;
    }

    int refreshFeed = -1;

    // Check latest feed from other users
    private void refreshFeed() throws IOException, ClassNotFoundException{
        resetClientFeedIndex();
        refreshFeed++;
        Request request = new Request(TagTypes.CLIENT,session, RequestType.GETFEED, BodyType.FEED_INDEX, refreshFeed);
        sendRequest(request);
    }

    private String calledClient = "";
    private  int client_index = -1;

    // Check client's feed you follow
    private void checkClient() throws IOException, ClassNotFoundException{
        resetRefreshFeedIndex();
        System.out.println("Enter client you wish to check");
        String client_id = scanner.nextLine();
        if(!client_id.equals(calledClient)) client_index = -1;
        calledClient = client_id;
        client_index++;
        Request request = new Request(TagTypes.CLIENT,session, RequestType.GET_USER_FEED, BodyType.CLIENT_ID_INT, client_id+"_"+client_index);
        sendRequest(request);
    }

    private void resetFeedIndex(){
        resetRefreshFeedIndex();
        resetClientFeedIndex();
    }

    private void resetRefreshFeedIndex(){
        refreshFeed = -1;
    }

    private void resetClientFeedIndex(){
        client_index = -1;
    }

    private boolean unauthorized(Response res){
        if(res.getResponse().equals(ResponseType.UNAUTHORIZED)){
            System.out.println("Your session is expired. Try to log in again.");
            session = null;
            return true;
        }
        return false;
    }

    private Response sendRequest(Request req) throws IOException, ClassNotFoundException{
        if(!connect(server)) return null;
        send(req);
        Response response = (Response) in.readObject();
        if(unauthorized(response)) {
            disconnect(socket);
            return null;
        }
        if(response.getBodyType().equals(BodyType.OUTPUT)) output((String)response.getBody());
        disconnect(socket);
        return response;
    }

    private void send(Request request) throws IOException{
        out.writeObject(request);
        out.flush();
    }

    private Credentials credential_input(){
        String userName; String password;
        System.out.print("Give username: ");
        userName = scanner.nextLine();
        System.out.print("Give password: ");
        password = scanner.nextLine();
        if(userName.isBlank() || userName.isEmpty() || password.isEmpty() || password.isBlank()){
            System.err.println("Provide valid credentials to register.");
            return null;
        }
        return new Credentials(userName, password);
    }

    private void output(String message){
        System.out.println(message);
    }

    private static final String cnf = "./serverConfig.txt";

    public static void main(String[] args){
        new Thread(new Client(cnf)).start();
    }

}
