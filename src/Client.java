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

    public Client(String config){
        this.config = config;
        server = reader.readServerInfo(config);
    }

    private void printMenu(){
        System.out.println("1. Send request");
        System.out.println("0. Exit");
    }

    private Socket connect(ServerInfo serverInfo){
        Socket socket = null;
        try{
            socket = new Socket(serverInfo.getAddress(), serverInfo.getPort());
        }catch(IOException e){
            System.out.println("Failed to connect to "+serverInfo.toString());
        }
        return socket;
    }

    private void request() throws IOException{
        Socket socket = connect(server);
        if (socket == null){
            System.out.println("Failed to connect to server: "+server.toString());
            return;
        }
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        String req = "";
        do{
            System.out.print("amen:>");
            req = scanner.nextLine();
            System.out.println();
            out.writeUTF(req);
            out.flush();
        }while (!req.equals("exit"));
        out.close();
        in.close();
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
                        request();
                        break;
                    default:
                        break;
                }
            }catch (IOException e){
                System.err.println("Something went wrong during connection.");
            }
        }while (!input.equals("0"));
        System.out.println("Closing client...");
    }

    private static final String cnf = "./serverConfig.txt";

    public static void main(String[] args){
        new Thread(new Client(cnf)).start();
    }

}
