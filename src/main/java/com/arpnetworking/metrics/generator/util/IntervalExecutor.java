/**
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

import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.generator.client.GeneratorSink;
import com.arpnetworking.metrics.generator.uow.UnitOfWorkSchedule;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

/**
 * Generates metrics for a given interval.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class IntervalExecutor {

    /**
     * Public constructor.
     *
     * @param start Interval start time.
     * @param end Interval end time.
     * @param generators List of UOW generators.
     * @param outputPath File to write metrics to.
     * @param clusterName The cluster to generate metrics for.
     * @param serviceName The service to generate metrics for.
     */
    public IntervalExecutor(
            final ZonedDateTime start,
            final ZonedDateTime end,
            final List<UnitOfWorkSchedule> generators,
            final Path outputPath,
            final String clusterName,
            final String serviceName) {
        final long nanoStart = TimeUnit.NANOSECONDS.convert(start.toInstant().toEpochMilli(), TimeUnit.MILLISECONDS);
        _nanoEnd = TimeUnit.NANOSECONDS.convert(end.toInstant().toEpochMilli(), TimeUnit.MILLISECONDS);
        _workEntries = new PriorityQueue<>(generators.size(), new WorkItemOrdering());
        for (final UnitOfWorkSchedule generator : generators) {
            final long unitStart = generator.getScheduler().next(nanoStart);
            _workEntries.add(new WorkEntry(generator, unitStart));
        }
        _modifyingSink = new GeneratorSink(outputPath, start);
        _metricsFactory = new TsdMetricsFactory.Builder()
                .setClusterName(clusterName)
                .setServiceName(serviceName)
                .setSinks(Collections.singletonList(_modifyingSink))
                .build();
    }

    /**
     * Generates the data for the metrics interval.
     */
    public void execute() {
        while (!_workEntries.isEmpty()) {
            final WorkEntry entry = _workEntries.poll();
            _modifyingSink.setTime(
                    ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(entry.getCurrentValue(), TimeUnit.NANOSECONDS)),
                            ZoneOffset.UTC));
            entry.getSchedule().getGenerator().generate(_metricsFactory);
            final WorkEntry newEntry = new WorkEntry(
                    entry.getSchedule(),
                    entry.getSchedule().getScheduler().next(entry.getCurrentValue()));
            // If the current execution time is within the bounds, enqueue the next
            // This makes sure that each generator is run once outside of the period to close it's period.
            if (entry.getCurrentValue() <= _nanoEnd) {
                _workEntries.add(newEntry);
            }
        }
        _modifyingSink.flush();
    }

    private final long _nanoEnd;
    private final PriorityQueue<WorkEntry> _workEntries;
    private final MetricsFactory _metricsFactory;
    private final GeneratorSink _modifyingSink;
}
