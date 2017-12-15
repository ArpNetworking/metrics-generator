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
package com.arpnetworking.metrics.generator.client;

import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;
// CHECKSTYLE.OFF: RegexpSingleline - These are included here for generation.
import com.arpnetworking.metrics.filesinkextra.shaded.ch.qos.logback.classic.LoggerContext;
import com.arpnetworking.metrics.filesinkextra.shaded.ch.qos.logback.classic.spi.ILoggingEvent;
import com.arpnetworking.metrics.filesinkextra.shaded.ch.qos.logback.core.FileAppender;
import com.arpnetworking.metrics.filesinkextra.shaded.com.arpnetworking.logback.StenoEncoder;
// CHECKSTYLE.ON: RegexpSingleline
import com.arpnetworking.metrics.impl.FileSink;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a sink to allow modification of the timestamps.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class GeneratorSink implements Sink {
    /**
     * Public constructor.
     *
     * @param outputPath The file to write to.
     * @param initialTime The time to use in the replacement.
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // getParent() may return null
    public GeneratorSink(final Path outputPath, final DateTime initialTime) {
        _time = initialTime;
        final Path file = outputPath.toAbsolutePath().normalize();
        _wrapped = new FileSink.Builder()
                .setDirectory(file.getParent().toFile())
                .setName(Files.getNameWithoutExtension(file.toString()))
                .setExtension("." + Files.getFileExtension(file.toString()))
                .setImmediateFlush(true)
                .setAsync(false)
                .build();
        replaceFileAppender(_wrapped, outputPath);
    }

    public void setTime(final DateTime time) {
        _time = time;
    }

    @Override
    public void record(final Event event) {
        final HashMap<String, String> modified = Maps.newHashMap(event.getAnnotations());
        modified.put("_start", _time.withZone(DateTimeZone.UTC).toString());
        modified.put("_end", _time.withZone(DateTimeZone.UTC).toString());
        _wrapped.record(new TimeWarpEvent(modified, event));
    }

    /**
     * Flushes unwritten data to disk.
     */
    public void flush() {
        // Do not call _appender.stop() and then _appender.start(), there is a bug
        // where that doesn't flush the file.
        _appender.getEncoder().stop();
        _appender.getEncoder().start();
    }

    private void replaceFileAppender(final Sink fileSink, final Path outputPath) {
        try {
            final Field queryLoggerField = fileSink.getClass().getSuperclass().getDeclaredField("_metricsLogger");
            queryLoggerField.setAccessible(true);
            final com.arpnetworking.metrics.filesinkextra.shaded.ch.qos.logback.classic.Logger queryLogger =
                    (com.arpnetworking.metrics.filesinkextra.shaded.ch.qos.logback.classic.Logger) queryLoggerField.get(fileSink);

            final Field contextField = fileSink.getClass().getSuperclass().getDeclaredField("_loggerContext");
            contextField.setAccessible(true);
            final LoggerContext loggerContext = (LoggerContext) contextField.get(fileSink);

            final StenoEncoder encoder = new StenoEncoder();
            encoder.setContext(loggerContext);

            final FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setAppend(false);
            fileAppender.setFile(outputPath.toAbsolutePath().toString());
            fileAppender.setName("hijacked-query-log");
            fileAppender.setContext(loggerContext);
            fileAppender.setEncoder(encoder);
            fileAppender.setImmediateFlush(true);

            encoder.start();
            fileAppender.start();

            _appender = fileAppender;

            queryLogger.detachAppender("query-log-async");
            queryLogger.addAppender(fileAppender);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private FileAppender<ILoggingEvent> _appender;
    private DateTime _time;
    private final Sink _wrapped;

    private static final class TimeWarpEvent implements Event {

        private TimeWarpEvent(final HashMap<String, String> modified, final Event event) {
            _modified = modified;
            _event = event;
        }

        @Override
        public Map<String, String> getAnnotations() {
            return _modified;
        }

        @Override
        public Map<String, List<Quantity>> getTimerSamples() {
            return _event.getTimerSamples();
        }

        @Override
        public Map<String, List<Quantity>> getCounterSamples() {
            return _event.getCounterSamples();
        }

        @Override
        public Map<String, List<Quantity>> getGaugeSamples() {
            return _event.getGaugeSamples();
        }

        private final HashMap<String, String> _modified;
        private final Event _event;
    }
}
