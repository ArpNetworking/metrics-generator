/*
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.generator.metric.ConstantCountMetricGenerator;
import com.arpnetworking.metrics.generator.metric.ConstantMetricGenerator;
import com.arpnetworking.metrics.generator.metric.GaussianMetricGenerator;
import com.arpnetworking.metrics.generator.metric.MetricGenerator;
import com.arpnetworking.metrics.generator.name.SingleNameGenerator;
import com.arpnetworking.metrics.generator.name.SpecifiedName;
import com.arpnetworking.metrics.generator.schedule.ConstantTimeScheduler;
import com.arpnetworking.metrics.generator.uow.UnitOfWorkGenerator;
import com.arpnetworking.metrics.generator.uow.UnitOfWorkSchedule;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.apache.commons.math3.random.RandomGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to generate a file for use in performance testing.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public final class TestFileGenerator {
    private TestFileGenerator(final Builder builder) {
        _random = builder._random;
        _uowCount = builder._uowCount;
        _namesCount = builder._namesCount;
        _samplesCount = builder._samplesCount;
        _startTime = builder._startTime;
        _endTime = builder._endTime;
        _fileName = builder._fileName;
        _clusterName = builder._clusterName;
        _serviceName = builder._serviceName;
    }

    /**
     * Generates the test file.
     */
    public void generate() {
        try {
            Files.deleteIfExists(_fileName);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final long totalSampleCount = ((long) _uowCount) * _namesCount * _samplesCount;
        LOGGER.info()
                .setEvent("GeneratingFile")
                .setMessage("Starting file generation")
                .addData("file", _fileName.toAbsolutePath())
                .addData("expectedSamples", totalSampleCount)
                .log();

        final Duration duration = Duration.between(_startTime, _endTime);

        final List<MetricGenerator> metricGenerators = Lists.newArrayList();
        for (int x = 0; x < _namesCount; ++x) {
            final GaussianMetricGenerator gaussian = new GaussianMetricGenerator(
                    50d, 8d, new SingleNameGenerator(_random), _random);
            final ConstantCountMetricGenerator sampleGenerator = new ConstantCountMetricGenerator(_samplesCount, gaussian);
            metricGenerators.add(sampleGenerator);
        }
        final UnitOfWorkGenerator uowGenerator = new UnitOfWorkGenerator(metricGenerators);

        final List<UnitOfWorkSchedule> schedules = Lists.newArrayList();
        final long durationInNanos = TimeUnit.NANOSECONDS.convert(duration.toMillis(), TimeUnit.MILLISECONDS);
        final long periodInNanos = durationInNanos / _uowCount;
        schedules.add(new UnitOfWorkSchedule(uowGenerator, new ConstantTimeScheduler(periodInNanos)));

        final MetricGenerator canary = new ConstantMetricGenerator(5, new SpecifiedName(CANARY));

        // Special canary unit of work schedulers
        // Each UOW generator is guaranteed to be executed once
        final UnitOfWorkGenerator canaryUOW = new UnitOfWorkGenerator(Collections.singletonList(canary));
        schedules.add(new UnitOfWorkSchedule(canaryUOW, new ConstantTimeScheduler(durationInNanos + periodInNanos)));

        final IntervalExecutor executor = new IntervalExecutor(
                _startTime,
                _endTime,
                schedules,
                _fileName,
                _clusterName,
                _serviceName);
        executor.execute();
        try {
            final BasicFileAttributes attributes = Files.readAttributes(_fileName, BasicFileAttributes.class);
            LOGGER.info()
                    .setEvent("GenerationComplete")
                .setMessage("Generation completed successfully")
                .addData("size", attributes.size())
                .log();
        } catch (final IOException e) {
            LOGGER.warn()
                    .setEvent("GenerationComplete")
                    .setMessage("Generation completed successfully but unable to read attributes of generated file")
                .setThrowable(e)
                .log();
        }
    }

    /**
     * Name of the ending canary metric.
     */
    public static final String CANARY = "endCanary";

    private final RandomGenerator _random;
    private final Integer _uowCount;
    private final Integer _namesCount;
    private final Integer _samplesCount;
    private final ZonedDateTime _startTime;
    private final ZonedDateTime _endTime;
    private final Path _fileName;
    private final String _clusterName;
    private final String _serviceName;

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFileGenerator.class);

    /**
     * Builder for a {@link TestFileGenerator}.
     */
    public static class Builder extends OvalBuilder<TestFileGenerator> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(TestFileGenerator::new);
        }

        /**
         * Sets the random generator.
         *
         * @param random The random generator.
         * @return This builder.
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "we actually want to be able to control the random")
        public Builder setRandom(final RandomGenerator random) {
            _random = random;
            return this;
        }

        /**
         * Sets the unit of work count.
         *
         * @param uowCount Unit of work count.
         * @return This builder.
         */
        public Builder setUnitOfWorkCount(final Integer uowCount) {
            _uowCount = uowCount;
            return this;
        }

        /**
         * Sets the names count.
         *
         * @param namesCount The names count
         * @return This builder.
         */
        public Builder setNamesCount(final Integer namesCount) {
            _namesCount = namesCount;
            return this;
        }

        /**
         * Sets the samples count.
         *
         * @param samplesCount The samples count
         * @return This builder.
         */
        public Builder setSamplesCount(final Integer samplesCount) {
            _samplesCount = samplesCount;
            return this;
        }

        /**
         * Sets the start time.
         *
         * @param startTime The start time
         * @return This builder.
         */
        public Builder setStartTime(final ZonedDateTime startTime) {
            _startTime = startTime;
            return this;
        }

        /**
         * Sets the end time.
         *
         * @param endTime The end time
         * @return This builder.
         */
        public Builder setEndTime(final ZonedDateTime endTime) {
            _endTime = endTime;
            return this;
        }

        /**
         * Sets the file name.
         *
         * @param fileName The file name
         * @return This builder.
         */
        public Builder setFileName(final Path fileName) {
            _fileName = fileName;
            return this;
        }

        /**
         * Sets the cluster name.
         *
         * @param clusterName The cluster name
         * @return This builder.
         */
        public Builder setClusterName(final String clusterName) {
            _clusterName = clusterName;
            return this;
        }

        /**
         * Sets the service name.
         *
         * @param serviceName The service name
         * @return This builder.
         */
        public Builder setServiceName(final String serviceName) {
            _serviceName = serviceName;
            return this;
        }

        /**
         * Build the {@link TestFileGenerator}.
         *
         * @return the new {@link TestFileGenerator}
         */
        @Override
        public TestFileGenerator build() {
            return new TestFileGenerator(this);
        }

        @NotNull
        private RandomGenerator _random;
        @Min(1)
        private Integer _uowCount;
        @Min(1)
        private Integer _namesCount;
        @Min(1)
        private Integer _samplesCount;
        @NotNull
        private ZonedDateTime _startTime;
        @NotNull
        private ZonedDateTime _endTime;
        @NotNull
        private Path _fileName;
        @NotNull
        @NotEmpty
        private String _clusterName;
        @NotNull
        @NotEmpty
        private String _serviceName;
    }
}
