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
package net.morimekta.gittool.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * General gittool utilities.
 */
public class Utils {
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
            return new File(Utils.class.getProtectionDomain()
                                       .getCodeSource()
                                       .getLocation()
                                       .toURI()
                                       .getPath())
                    .getCanonicalFile()
                    .getParentFile()
                    .getAbsoluteFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
