import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class NodeReader {

    public NodeReader(){}

    public ServerInfo readServerInfo(String path){
        ServerInfo server = null;
        FileReader fr = null;
        BufferedReader br = null;
        try {

            fr = new FileReader(path);
            br = new BufferedReader(fr);
            String readLine;

            while ((readLine = br.readLine()) != null) {
                if(readLine.matches("\\s+")) continue;
                String[] parts = readLine.split("\\s");
                try {
                    server = new ServerInfo(parts[0], Integer.parseInt(parts[1]));
                }catch(Exception x){
                    System.err.println("Port is not a number");
                }
            }
        } catch (FileNotFoundException o) {
            o.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) br.close();
                if (fr != null) fr.close();
            } catch (IOException ioE) {
                ioE.printStackTrace();
            }
        }
        return server;
    }
}
