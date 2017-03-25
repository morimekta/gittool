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
package net.morimekta.gittool.gt.cmd;

import net.morimekta.console.args.ArgumentParser;
import net.morimekta.gittool.gt.GitTool;

/**
 * Base class for all commands.
 */
public abstract class Command {
    private final ArgumentParser parent;

    Command(ArgumentParser parent) {
        this.parent = parent;
    }

    /**
     * Make argument parser for the command.
     * @return The commands argument parser.
     */
    public abstract ArgumentParser makeParser();

    /**
     * Execute command with the main GitTool options op.
     * @param opts The main gittool options.
     * @throws Exception
     */
    public abstract void execute(GitTool opts) throws Exception;

    protected ArgumentParser getParent() {
        return parent;
    }
}
