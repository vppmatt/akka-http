import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MarshallingAndUnmarshalling {

    public static void main(String[] args) {
        Customer customer = new Customer(17,"Matt", "Greencroft", 21, true);
        String json = "";
        try {
            json = new ObjectMapper().writeValueAsString(customer);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        System.out.println(json);

        try {
            Customer customer2 = new ObjectMapper().readValue(json, Customer.class);
            System.out.println(customer2);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }
}
