/*
 * Copyright 2017 (c) Stein Eldar Johnsen
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
package net.morimekta.gittool.cmd;

import net.morimekta.gittool.GitTool;
import net.morimekta.strings.chr.Color;

/**
 * Base class for all commands.
 */
public abstract class Command {
    static final Color WARN = new Color(Color.YELLOW, Color.BOLD);
    static final Color ERRO = new Color(Color.RED, Color.BOLD);

    static final Color CLR_UPDATED_BRANCH = new Color(Color.YELLOW, Color.BOLD);
    static final Color CLR_BASE_BRANCH    = new Color(Color.YELLOW, Color.DIM);

    static final Color CLR_ADDS = new Color(Color.GREEN, Color.BOLD);
    static final Color CLR_SUBS = new Color(Color.RED, Color.BOLD);

    /**
     * Execute command with the main GitTool options op.
     *
     * @param opts The main gittool options.
     * @throws Exception
     */
    public abstract void execute(GitTool opts) throws Exception;
}
