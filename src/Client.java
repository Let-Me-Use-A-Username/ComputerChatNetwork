import com.objects.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client implements  Runnable{

    private final NodeReader reader = new NodeReader();
    private ServerInfo server;
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
        String input = "";
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
                    default:
                        break;
                }
            }catch (IOException | ClassNotFoundException e){
                System.err.println("Something went wrong during connection.");
            }
        }while (!input.equals("0"));
        System.out.println("Closing client...");
    }

    private void register() throws IOException, ClassNotFoundException {
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

    private void login() throws IOException, ClassNotFoundException {
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

    private void send(Request request) throws IOException{
        out.writeObject(request);
        out.flush();
    }

    private Credentials credential_input(){
        String userName = ""; String password = "";
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
