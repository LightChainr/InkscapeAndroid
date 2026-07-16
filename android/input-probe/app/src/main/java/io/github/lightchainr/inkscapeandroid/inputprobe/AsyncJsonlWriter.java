package io.github.lightchainr.inkscapeandroid.inputprobe;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class AsyncJsonlWriter {
    interface JsonRecord {
        long sequence();
        String toJsonLine() throws Exception;
    }

    private static final class FlushCommand {
        final CountDownLatch done = new CountDownLatch(1);
    }

    private static final class CloseCommand {
        final CountDownLatch done = new CountDownLatch(1);
    }

    private final String sessionId;
    private final int capacity;
    private final ArrayBlockingQueue<Object> queue;
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong lastAttemptedSequence = new AtomicLong();
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BufferedWriter writer;
    private final Thread worker;

    AsyncJsonlWriter(File file, String sessionId, int capacity) throws IOException {
        this.sessionId = sessionId;
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.writer = Files.newBufferedWriter(
                file.toPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        this.worker = new Thread(this::runWorker, "input-probe-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    boolean enqueue(JsonRecord record) {
        if (closed.get()) {
            return false;
        }
        lastAttemptedSequence.accumulateAndGet(record.sequence(), Math::max);
        if (!queue.offer(record)) {
            droppedRecords.incrementAndGet();
            return false;
        }
        return true;
    }

    boolean enqueueJson(String jsonLine) {
        return enqueue(new JsonRecord() {
            @Override
            public long sequence() {
                return 0;
            }

            @Override
            public String toJsonLine() {
                return jsonLine;
            }
        });
    }

    long pendingDropCount() {
        return droppedRecords.get();
    }

    int queueSize() {
        return queue.size();
    }

    String failureMessage() {
        return failure.get();
    }

    void flush() {
        if (closed.get()) {
            return;
        }
        FlushCommand command = new FlushCommand();
        if (!offerControl(command)) {
            return;
        }
        await(command.done);
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        CloseCommand command = new CloseCommand();
        if (offerControl(command)) {
            await(command.done);
        }
        try {
            worker.join(2000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean offerControl(Object command) {
        try {
            return queue.offer(command, 500L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, "interrupted while queueing writer control command");
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runWorker() {
        try {
            boolean running = true;
            while (running) {
                Object item = queue.take();
                emitDropRecordIfNeeded();
                if (item instanceof JsonRecord record) {
                    writer.write(record.toJsonLine());
                    writer.newLine();
                } else if (item instanceof FlushCommand flush) {
                    writer.flush();
                    flush.done.countDown();
                } else if (item instanceof CloseCommand close) {
                    writer.flush();
                    close.done.countDown();
                    running = false;
                }
            }
        } catch (Exception error) {
            failure.compareAndSet(null, error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            try {
                emitDropRecordIfNeeded();
                writer.flush();
                writer.close();
            } catch (Exception error) {
                failure.compareAndSet(null, error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }
    }

    private void emitDropRecordIfNeeded() throws Exception {
        long count = droppedRecords.getAndSet(0L);
        if (count == 0L) {
            return;
        }
        JSONObject json = new JSONObject();
        json.put("schemaVersion", EventSample.SCHEMA_VERSION);
        json.put("recordType", "drop");
        json.put("sessionId", sessionId);
        json.put("afterSequence", lastAttemptedSequence.get());
        json.put("droppedRecords", count);
        json.put("queueCapacity", capacity);
        writer.write(json.toString());
        writer.newLine();
    }
}
