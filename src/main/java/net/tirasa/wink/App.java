package net.tirasa.wink;

import net.tirasa.wink.client.asynchttpclient.ApacheHttpAsyncClientConfig;
import net.tirasa.wink.client.asynchttpclient.FutureClientResponse;
import com.ning.http.client.AsyncHttpClient;
import javax.ws.rs.core.MediaType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.wink.client.AsyncHttpClientConfiguration;
import org.apache.wink.client.httpclient.ApacheHttpClientConfig;
import org.apache.wink.client.Resource;
import org.apache.wink.client.RestClient;
import org.apache.wink.common.model.atom.AtomEntry;
import org.apache.wink.common.model.atom.AtomFeed;

public class App {

    /**
     * Use Apache Commons HttpClient (supported by Apache Wink 1.3.0).
     *
     * @see http://hc.apache.org/httpcomponents-client-ga/index.html
     */
    public static void httpClientGet() {
        RestClient client = new RestClient(new ApacheHttpClientConfig(HttpClientBuilder.create().build()));

        Resource resource =
                client.resource("http://services.odata.org/v3/(S(sn4zeecdefwvblk2xxlk425x))/OData/OData.svc/Products");
        AtomFeed feed = resource.accept(MediaType.APPLICATION_ATOM_XML).get(AtomFeed.class);

        for (AtomEntry entry : feed.getEntries()) {
            System.out.println(entry.getTitle().getValue());
        }
    }

    /**
     * Use Ning AsyncClient (supported by Apache Wink 1.3.0).
     *
     * @see https://github.com/AsyncHttpClient/async-http-client
     */
    public static void asyncHttpClientGet() {
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        RestClient client = new RestClient(new AsyncHttpClientConfiguration(asyncHttpClient));

        Resource resource =
                client.resource("http://services.odata.org/v3/(S(sn4zeecdefwvblk2xxlk425x))/OData/OData.svc/Products");
        AtomFeed feed = resource.accept(MediaType.APPLICATION_ATOM_XML).get(AtomFeed.class);
        asyncHttpClient.close();

        for (AtomEntry entry : feed.getEntries()) {
            System.out.println(entry.getTitle().getValue());
        }
    }

    /**
     * Use Apache Commons HttpAsyncClient (supported by Apache Wink 1.3.0).
     *
     * @see http://hc.apache.org/httpcomponents-asyncclient-dev/
     */
    public static void asyncApacheHttpClientGet() throws Exception {
        RestClient client = new RestClient(new ApacheHttpAsyncClientConfig());

        Resource resource =
                client.resource("http://services.odata.org/v3/(S(sn4zeecdefwvblk2xxlk425x))/OData/OData.svc/Products");
        FutureClientResponse response = (FutureClientResponse) resource.accept(MediaType.APPLICATION_ATOM_XML).get();

        while (!response.isDone()) {
            System.out.println("Not yet done.");
        }

        AtomFeed feed = response.get().getEntity(AtomFeed.class);

        for (AtomEntry entry : feed.getEntries()) {
            System.out.println(entry.getTitle().getValue());
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("*********** Sync get via Apache Commons HttpClient");
        httpClientGet();

        System.out.println("\n*********** Async get via Ning AsyncHttpClient");
        asyncHttpClientGet();

        System.out.println("\n*********** Async get via Apache Commons AsyncHttpClient");
        asyncApacheHttpClientGet();
    }
}
