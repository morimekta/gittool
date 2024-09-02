/*
 * Copyright 2024 (c) Stein Eldar Johnsen
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

import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.YELLOW;

public class Colors {
    public static final Color GREEN_BOLD  = new Color(Color.GREEN, Color.BOLD);
    public static final Color RED_BOLD    = new Color(Color.RED, Color.BOLD);
    public static final Color YELLOW_BOLD = new Color(YELLOW, BOLD);

    public static final Color YELLOW_DIM = new Color(YELLOW, DIM);
}
