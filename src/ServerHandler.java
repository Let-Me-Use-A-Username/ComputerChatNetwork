import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ServerHandler implements Runnable{


    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ServerHandler(Socket socket){
        this.socket = socket;
    }

    public void run(){
        try{
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            while(true){
                System.out.println(in.readUTF());
            }
        }catch (IOException e){
            System.err.println("Socket must have been closed.");
        }finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            }catch (IOException e){
                System.err.println("Failed to close the Handler's streams.");
            }
        }
    }

}
