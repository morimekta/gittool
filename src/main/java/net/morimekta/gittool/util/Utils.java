/*
 * Copyright (c) 2016, Stein Eldar Johnsen
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package net.morimekta.gittool.util;

import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import static java.lang.String.format;
import static net.morimekta.gittool.util.Colors.GREEN_BOLD;
import static net.morimekta.gittool.util.Colors.RED_BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;

/**
 * General gittool utilities.
 */
public class Utils {
    public static void clr(StringBuilder builder, Color baseColor) {
        builder.append(CLEAR);
        if (baseColor != null) {
            builder.append(baseColor);
        }
    }

    public static String clr(Color baseColor) {
        if (baseColor != null) {
            return CLEAR.toString() + baseColor;
        } else {
            return CLEAR.toString();
        }
    }

    public static String addsAndDeletes(int adds, int deletes, Color baseColor) {
        if (adds > 0 || deletes > 0) {
            if (adds == 0) {
                return format("[%s-%d%s]", RED_BOLD, deletes, clr(baseColor));
            } else if (deletes == 0) {
                return format("[%s+%d%s]", GREEN_BOLD, adds, clr(baseColor));
            } else {
                return format("[%s+%d%s,%s-%d%s]",
                              GREEN_BOLD,
                              adds,
                              clr(baseColor),
                              RED_BOLD,
                              deletes,
                              clr(baseColor));
            }
        }
        return "";
    }

    public static String date(RevCommit commit) {
        Clock clock = Clock.systemDefaultZone();
        ZonedDateTime instant = Instant.ofEpochSecond(commit.getCommitTime()).atZone(clock.getZone());
        ZonedDateTime midnight = Instant.now()
                                        .atZone(clock.getZone())
                                        .withHour(0)
                                        .withMinute(0)
                                        .withSecond(0)
                                        .withNano(0);

        if (instant.isBefore(midnight.minusDays(1))) {
            // before yesterday
            return DateTimeFormatter.ISO_LOCAL_DATE.format(instant);
        } else if (instant.isBefore(midnight)) {
            // yesterday, close enough so that time matter.
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant).replaceAll("[T]", " ");
        } else {
            return DateTimeFormatter.ISO_LOCAL_TIME.format(instant);
        }
    }

    public static int countIter(Iterable<?> iter) {
        int count = 0;
        var iterator = iter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    public static String versionString() {
        Properties properties = new Properties();
        try (InputStream in = Utils.class.getResourceAsStream("/build.properties")) {
            properties.load(in);
            return "v" + properties.getProperty("build.version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static File shareLocation() {
        try {
            // If run from the 'normal' jar in the share directory, then the location of the
            // jar file is the share directory for gittool.
            File location = new File(Utils.class.getProtectionDomain()
                                                .getCodeSource()
                                                .getLocation()
                                                .toURI()
                                                .getPath())
                    .getCanonicalFile().getAbsoluteFile();
            if (location.isDirectory()) {
                // E.g. .../gittool-gt/target/classes
                // contains the resources locally
                return location;
            } else {
                // E.g. /usr/local/share/gittool/gt.jar
                return location.getParentFile();
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
