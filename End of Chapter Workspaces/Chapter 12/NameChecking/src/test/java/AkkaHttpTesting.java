import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class AkkaHttpTesting extends JUnitRouteTest {

    @Test // (expected = Exception.class)
    public void testAddingIsOk() {
        Route route = new Application().getRoutes();
        TestRoute testRoute = testRoute(route);
        HttpRequest request = HttpRequest.GET("/add?first=4&second=19");
        TestRouteResult result = testRoute.run(request);
        result.assertStatusCode(200);
        result.assertEntity("{\"total\":23}");

    }
}
