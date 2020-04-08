import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements Runnable{

    private static final NodeReader reader = new NodeReader();
    private ServerSocket serverSocket;
    private ServerInfo serverInfo;
    private String configPath;
    private int port;

    public Server(String configPath){
        this.configPath = configPath;
        serverInfo = reader.readServerInfo(configPath);
    }

    public void run(){
        try{
            serverSocket = new ServerSocket(serverInfo.getPort());
        }catch(IOException e){
            System.err.println("Couldn't start server at port "+serverInfo.getPort());
        }
        accept();
    }

    private void accept(){
        System.out.println("Server running at: "+serverInfo.toString());
        try{
            while(true) {
                Socket socket = serverSocket.accept();
                new Thread(new ServerHandler(socket)).start();
            }
        }catch(IOException e){
            System.out.println("Server closed");
        }
    }

    public void close(){
        try{
            serverSocket.close();
        }catch(IOException e){
            System.err.println("Couldn't close server.");
        }
    }

    private static final String conf_path = "./serverConfig.txt";

    public static void main(String[] args){
        new Thread(new Server(conf_path)).start();
    }

}
