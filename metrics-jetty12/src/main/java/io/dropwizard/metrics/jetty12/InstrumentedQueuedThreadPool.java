package io.dropwizard.metrics.jetty12;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.RatioGauge;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;

import static io.dropwizard.metrics5.MetricRegistry.name;

public class InstrumentedQueuedThreadPool extends QueuedThreadPool {
    private static final String NAME_UTILIZATION = "utilization";
    private static final String NAME_UTILIZATION_MAX = "utilization-max";
    private static final String NAME_SIZE = "size";
    private static final String NAME_JOBS = "jobs";
    private static final String NAME_JOBS_QUEUE_UTILIZATION = "jobs-queue-utilization";

    private final MetricRegistry metricRegistry;
    private String prefix;

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry) {
        this(registry, 200);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads) {
        this(registry, maxThreads, 8);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads) {
        this(registry, maxThreads, minThreads, 60000);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("queue") BlockingQueue<Runnable> queue) {
        this(registry, maxThreads, minThreads, 60000, queue);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout) {
        this(registry, maxThreads, minThreads, idleTimeout, null);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("queue") BlockingQueue<Runnable> queue) {
        this(registry, maxThreads, minThreads, idleTimeout, queue, (ThreadGroup) null);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("queue") BlockingQueue<Runnable> queue,
                                        @Name("threadFactory") ThreadFactory threadFactory) {
        this(registry, maxThreads, minThreads, idleTimeout, -1, queue, null, threadFactory);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("queue") BlockingQueue<Runnable> queue,
                                        @Name("threadGroup") ThreadGroup threadGroup) {
        this(registry, maxThreads, minThreads, idleTimeout, -1, queue, threadGroup);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("reservedThreads") int reservedThreads,
                                        @Name("queue") BlockingQueue<Runnable> queue,
                                        @Name("threadGroup") ThreadGroup threadGroup) {
        this(registry, maxThreads, minThreads, idleTimeout, reservedThreads, queue, threadGroup, null);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("reservedThreads") int reservedThreads,
                                        @Name("queue") BlockingQueue<Runnable> queue,
                                        @Name("threadGroup") ThreadGroup threadGroup,
                                        @Name("threadFactory") ThreadFactory threadFactory) {
        this(registry, maxThreads, minThreads, idleTimeout, reservedThreads, queue, threadGroup, threadFactory, null);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") MetricRegistry registry,
                                        @Name("maxThreads") int maxThreads,
                                        @Name("minThreads") int minThreads,
                                        @Name("idleTimeout") int idleTimeout,
                                        @Name("reservedThreads") int reservedThreads,
                                        @Name("queue") BlockingQueue<Runnable> queue,
                                        @Name("threadGroup") ThreadGroup threadGroup,
                                        @Name("threadFactory") ThreadFactory threadFactory,
                                        @Name("prefix") String prefix) {
        super(maxThreads, minThreads, idleTimeout, reservedThreads, queue, threadGroup, threadFactory);
        this.metricRegistry = registry;
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final MetricName prefix = getMetricPrefix();

        metricRegistry.register(prefix.resolve(NAME_UTILIZATION), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(getThreads() - getIdleThreads(), getThreads());
            }
        });
        metricRegistry.register(prefix.resolve(NAME_UTILIZATION_MAX), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(getThreads() - getIdleThreads(), getMaxThreads());
            }
        });
        metricRegistry.registerGauge(prefix.resolve(NAME_SIZE), this::getThreads);
        // This assumes the QueuedThreadPool is using a BlockingArrayQueue or
        // ArrayBlockingQueue for its queue, and is therefore a constant-time operation.
        metricRegistry.registerGauge(prefix.resolve(NAME_JOBS), () -> getQueue().size());
        metricRegistry.register(prefix.resolve(NAME_JOBS_QUEUE_UTILIZATION), new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                BlockingQueue<Runnable> queue = getQueue();
                return Ratio.of(queue.size(), queue.size() + queue.remainingCapacity());
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        final MetricName prefix = getMetricPrefix();

        metricRegistry.remove(prefix.resolve(NAME_UTILIZATION));
        metricRegistry.remove(prefix.resolve(NAME_UTILIZATION_MAX));
        metricRegistry.remove(prefix.resolve(NAME_SIZE));
        metricRegistry.remove(prefix.resolve(NAME_JOBS));
        metricRegistry.remove(prefix.resolve(NAME_JOBS_QUEUE_UTILIZATION));

        super.doStop();
    }

    private MetricName getMetricPrefix() {
        return this.prefix == null ? name(QueuedThreadPool.class, getName()) : name(this.prefix, getName());
    }
}