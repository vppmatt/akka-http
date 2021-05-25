import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetRequestTask implements Runnable {
    private int id;

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        try {
            System.out.println("starting thread " + id);
            URL url = new URL("http://localhost:8080/");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            //con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            //String jsonInputString = "{\"accountNumber\":\"123\",\"amount\":\"1000.00\"}";

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("response received from thread " + id);
                System.out.println(response.toString());
            }
        }
        catch(Exception e) {
            //ignoring exceptions
        }
    }

}
