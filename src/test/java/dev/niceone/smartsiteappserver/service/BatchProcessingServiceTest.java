package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.utils.PerformanceLogger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchProcessingServiceTest {

    @Test
    void smartBatchProcessesEveryItemOnceWhileTheBatchGrows() throws Exception {
        BatchProcessingService service = new BatchProcessingService();
        ReflectionTestUtils.setField(service, "performanceLogger", new PerformanceLogger());
        List<Integer> items = IntStream.range(0, 2936).boxed().collect(Collectors.toList());
        List<Integer> seen = new ArrayList<>();

        int processed = service.processSmartBatch(items, seen::add, "test").get();

        assertEquals(items.size(), processed);
        assertEquals(items, seen);
    }
}
