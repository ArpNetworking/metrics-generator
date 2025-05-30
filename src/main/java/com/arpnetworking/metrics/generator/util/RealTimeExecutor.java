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

import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.generator.client.GeneratorSink;
import com.arpnetworking.metrics.generator.uow.UnitOfWorkSchedule;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.google.common.collect.Lists;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

/**
 * Executes a scheduler in real-time.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class RealTimeExecutor {
    /**
     * Public constructor.
     *
     * @param generators List of UOW generators.
     * @param outputPath File to write metrics to.
     * @param clusterName The cluster to generate metrics for.
     * @param serviceName The service to generate metrics for.
     */
    public RealTimeExecutor(
            final List<UnitOfWorkSchedule> generators,
            final Path outputPath,
            final String clusterName,
            final String serviceName) {
        _generators = Lists.newArrayList(generators);
        _workEntries = new PriorityQueue<>(generators.size(), new WorkItemOrdering());
        _modifyingSink = new GeneratorSink(outputPath, ZonedDateTime.now());

        _metricsFactory = new TsdMetricsFactory.Builder()
                .setClusterName(clusterName)
                .setServiceName(serviceName)
                .setSinks(Collections.singletonList(_modifyingSink))
                .build();
    }

    /**
     * Generates metrics.
     */
    public void execute() {
        for (final UnitOfWorkSchedule generator : _generators) {
            final long unitStart = generator.getScheduler().next(
                    TimeUnit.NANOSECONDS.convert(ZonedDateTime.now().toInstant().toEpochMilli(), TimeUnit.MILLISECONDS));
            _workEntries.add(new WorkEntry(generator, unitStart));
        }
        while (true) {
            if (_workEntries.isEmpty()) {
                break;
            }
            final WorkEntry entry = _workEntries.peek();
            final ZonedDateTime executeTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(entry.getCurrentValue(), TimeUnit.NANOSECONDS)),
                    ZoneOffset.UTC);
            if (executeTime.isAfter(ZonedDateTime.now())) {
                try {
                    Thread.sleep(10);
                } catch (final InterruptedException ignored) {
                    Thread.interrupted();
                    return;
                }
                continue;
            }
            _workEntries.poll();
            _modifyingSink.setTime(ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(entry.getCurrentValue(), TimeUnit.NANOSECONDS)),
                    ZoneOffset.UTC));
            entry.getSchedule().getGenerator().generate(_metricsFactory);
            final WorkEntry newEntry = new WorkEntry(
                    entry.getSchedule(),
                    entry.getSchedule().getScheduler().next(entry.getCurrentValue()));
            _workEntries.add(newEntry);
        }
    }

    private final GeneratorSink _modifyingSink;
    private final MetricsFactory _metricsFactory;
    private final PriorityQueue<WorkEntry> _workEntries;
    private final List<UnitOfWorkSchedule> _generators;
}
