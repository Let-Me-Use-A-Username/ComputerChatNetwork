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

    public ServerHandler(Socket socket){
        this.socket = socket;
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
                        register();
                        break;
                    case LOG_IN:
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

    private static final String registerOut = "Please give me your credentials, to register";
    private static final String userExists = "User id, already exists.";

    private void register() throws IOException, ClassNotFoundException {
        Request request = new Request(TagTypes.SERVER,null, RequestType.CREDENTIALS, BodyType.OUTPUT,registerOut);
        out.writeObject(request);
        out.flush();
        Response response = (Response) in.readObject();
        Credentials credentials = (Credentials) response.getBody();
        boolean exists = userExists(credentials.getClientID());
        if(exists){
            out.writeObject(new Response(TagTypes.SERVER, ResponseType.BAD_REQUEST, BodyType.OUTPUT, userExists));
            out.flush();
            return;
        }
        write_register(credentials);
    }

    private final String credPath = "./database/ClientCredentials/index.txt";
    private boolean userExists(String clientID){
        boolean found = false;
        try{
            File credIndex = new File(credPath);
            // Check if file exists
            if(!credIndex.exists()){
                return !credIndex.createNewFile();
            }
            // Check all records
            FileReader fr = new FileReader(credIndex.getPath());
            BufferedReader br = new BufferedReader(fr);
            String readLine;
            while(true){
                synchronized (credLock){
                    readLine = br.readLine();
                }
                if(readLine == null) break;
                String[] parts = readLine.split("\\s+");
                if(parts[0].equals(clientID)) {
                    found = true;
                    break;
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
