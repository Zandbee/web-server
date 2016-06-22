import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by vstrokova on 22.06.2016.
 */
public class WebServerConnectionsCounter {
    private AtomicInteger counter = new AtomicInteger(0);

    public int increment() {
        return counter.incrementAndGet();
    }

    public int decrement() {
        return counter.decrementAndGet();
    }

    public int getValue() {
        return counter.get();
    }
}
