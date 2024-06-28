import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock;
    private final Condition condition;
    private final AtomicInteger requestCount;
    private final AtomicLong lastResetTime;
    private final int requestLimit;
    private final long interval;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.requestCount = new AtomicInteger(0);
        this.lastResetTime = new AtomicLong(Instant.now().toEpochMilli());
        this.requestLimit = requestLimit;
        this.interval = timeUnit.toMillis(1);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                long now = Instant.now().toEpochMilli();
                if (now - lastResetTime.get() >= interval) {
                    requestCount.set(0);
                    lastResetTime.set(now);
                } else {
                    condition.await();
                }
            }
            requestCount.incrementAndGet();

            HttpPost post = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(document), "UTF-8"));
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Signature", signature);

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                HttpEntity entity = response.getEntity();
                if (entity!= null) {
                    EntityUtils.consume(entity);
                }
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode!= 200) {
                    throw new IOException("Failed to create document: " + statusCode);
                }
            }
        } finally {
            lock.unlock();
        }
    }
    private static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;
    }

    private static class Description {
        private String participantInn;
    }

    private static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }


}
