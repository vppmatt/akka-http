import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetRequestTask implements Runnable {
    private int id;

    public void setId(int id) {
        this.id = id;
    }

    public String processRequest(String address) {
        try {
            URL url = new URL(address);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setDoOutput(true);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        }
        catch(Exception e) {
            //ignoring exceptions
            return null;
        }
    }

    @Override
    public void run() {
        System.out.println("starting thread " + id);
        String requestId = processRequest("http://localhost:8080");
        System.out.println("Request id for thread " + id + " is " + requestId);
        String result = "0";

        while (result.equals("0")) {
            System.out.println("Getting an update for request " + requestId);
            result = processRequest("http://localhost:8080/result/" + requestId);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Value received for requestId " + requestId);
        System.out.println(result);

    }

}
