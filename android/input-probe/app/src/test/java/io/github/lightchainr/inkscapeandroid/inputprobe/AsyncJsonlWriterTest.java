package io.github.lightchainr.inkscapeandroid.inputprobe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AsyncJsonlWriterTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesQueuedRecordsAndFlushes() throws Exception {
        File file = temporaryFolder.newFile("events.jsonl");
        AsyncJsonlWriter writer = new AsyncJsonlWriter(file, "session-test", 8);
        assertTrue(writer.enqueue(record(1, "{\"recordType\":\"test\",\"sequence\":1}")));
        assertTrue(writer.enqueue(record(2, "{\"recordType\":\"test\",\"sequence\":2}")));
        writer.flush();
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"sequence\":1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"sequence\":2")));
    }

    @Test
    public void emitsDropRecordWhenBoundedQueueOverflows() throws Exception {
        File file = temporaryFolder.newFile("events.jsonl");
        AsyncJsonlWriter writer = new AsyncJsonlWriter(file, "session-test", 1);

        CountDownLatch serializationStarted = new CountDownLatch(1);
        CountDownLatch releaseSerialization = new CountDownLatch(1);
        assertTrue(writer.enqueue(new AsyncJsonlWriter.JsonRecord() {
            @Override
            public long sequence() {
                return 1;
            }

            @Override
            public String toJsonLine() throws Exception {
                serializationStarted.countDown();
                if (!releaseSerialization.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test did not release serialization");
                }
                return "{\"recordType\":\"test\",\"sequence\":1}";
            }
        }));

        assertTrue(serializationStarted.await(2, TimeUnit.SECONDS));
        assertTrue(writer.enqueue(record(2, "{\"recordType\":\"test\",\"sequence\":2}")));
        assertFalse(writer.enqueue(record(3, "{\"recordType\":\"test\",\"sequence\":3}")));

        releaseSerialization.countDown();
        writer.flush();
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"recordType\":\"drop\"")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"droppedRecords\":1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"queueCapacity\":1")));
    }

    private static AsyncJsonlWriter.JsonRecord record(long sequence, String json) {
        return new AsyncJsonlWriter.JsonRecord() {
            @Override
            public long sequence() {
                return sequence;
            }

            @Override
            public String toJsonLine() {
                return json;
            }
        };
    }
}
