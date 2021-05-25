import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class GenerateTransactionTask implements Runnable {

    private int id;
    int transId = 0;
    private RestActions restActions = new RestActions();

    public void setId(int id) {
        this.id = id;
    }

    private void postTransaction() {
        String responseString =restActions.restPostOrPut("POST","http://localhost:8080/api/transaction","{\"accountNumber\":\"123\",\"amount\":\"1000.00\"}");
        String transPart = responseString.split(":")[1];
        transPart = transPart.substring(0, transPart.length() -1);
        System.out.println("Transaction posted with ID " + transPart);
        transId = Integer.parseInt(transPart);
    }

    private String getTransactionStatus() {
        String fullResponse = restActions.restGet("http://localhost:8080/api/transaction?id=" + transId);
        String status = fullResponse.split(",")[0].split(":")[1].replaceAll("\"","");
        return status;
    }

    @Override
    public void run() {
        postTransaction();

        boolean finished = false;

        while (!finished) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {

            }
            String status = getTransactionStatus();
            System.out.println("Current status of transaction " + transId + " is "  + status);
            if (status.equals("COMPLETE"))
                finished = true;
        }
    }

}
