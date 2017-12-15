/**
 * Copyright 2017 Inscope Metrics Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.generator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the <code>FileGenerator</code> class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class TestFileGeneratorTest {

    @Test
    public void test() throws IOException {
        final Path tempDir = Files.createTempDirectory("TestFileGeneratorTest");
        final Path tempFile = Files.createTempFile(tempDir, "test", ".tmp");

        final DateTime start = DateTime.now().minusDays(1).hourOfDay().roundFloorCopy();
        final DateTime stop = start.plusMinutes(10);
        final TestFileGenerator generator = new TestFileGenerator.Builder()
                .setRandom(RANDOM)
                .setUnitOfWorkCount(1)
                .setNamesCount(1)
                .setSamplesCount(10)
                .setStartTime(start)
                .setEndTime(stop)
                .setFileName(tempFile.toAbsolutePath())
                .setClusterName("test_cluster")
                .setServiceName("test_service")
                .build();
        generator.generate();

        final List<String> lines = Files.readAllLines(tempFile);
        Assert.assertEquals(3, lines.size());
        for (final String line : lines) {
            OBJECT_MAPPER.readTree(line);
        }
    }

    private static final RandomGenerator RANDOM = new MersenneTwister(1298);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
}
