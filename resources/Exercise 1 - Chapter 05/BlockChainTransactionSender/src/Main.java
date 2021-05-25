import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class Main {

    public static void main(String[] args) throws InterruptedException {
        RestActions restActions = new RestActions();
        List<Thread> threads = new ArrayList<>();

        //start 5 threads
        for (int i = 0; i < 5; i++) {
            GenerateTransactionTask task = new GenerateTransactionTask();
            task.setId(i);
            Thread t = new Thread(task);
            threads.add(t);
            t.start();
        }

        //after 3 seconds issue mining command
        Thread.sleep(3000);
        restActions.restPostOrPut("PUT","http://localhost:8080/api/mining?lastHash=1b2c", "");

        //start another 5 threads
        for (int i = 5; i < 10; i++) {
            GenerateTransactionTask task = new GenerateTransactionTask();
            task.setId(i);
            Thread t = new Thread(task);
            threads.add(t);
            t.start();
        }

        //after 5 seconds issue mining command
        Thread.sleep(5000);
        restActions.restPostOrPut("PUT","http://localhost:8080/api/mining?lastHash=1b2c", "");

        //after 5 seconds issue mining command
        Thread.sleep(5000);
        restActions.restPostOrPut("PUT","http://localhost:8080/api/mining?lastHash=1b2c", "");

        for (Thread t : threads) {
            t.join();
        }
    }

}
