package av.is.aegis;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadBuilder {

    private static final AtomicInteger WORKING_THREADS = new AtomicInteger();
    private static final AtomicInteger NON_COUNTABLE_THREADS = new AtomicInteger();

    private ThreadBuilder() {
    }

    public static ThreadBuilder builder() {
        return new ThreadBuilder();
    }

    static void incrementThreadsForStatistic(int threads) {
        WORKING_THREADS.addAndGet(threads);
        NON_COUNTABLE_THREADS.addAndGet(threads);
    }

    static int getThreads() {
        return WORKING_THREADS.get();
    }

    static int getNonCountableThreads() {
        return NON_COUNTABLE_THREADS.get();
    }

    private Runnable runnable;
    private String name;
    private boolean daemon = false;

    public ThreadBuilder runnable(Runnable runnable) {
        this.runnable = runnable;
        return this;
    }

    public ThreadBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ThreadBuilder daemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    public Thread build() {
        Thread thread = new Thread(() -> {
            runnable.run();
            WORKING_THREADS.decrementAndGet();
        });
        if(name != null) {
            thread.setName(name);
        }
        if(daemon) {
            thread.setDaemon(true);
        }
        return thread;
    }

    public void start() {
        build().start();
        WORKING_THREADS.incrementAndGet();
    }

}
