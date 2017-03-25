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
 * Interactively manage branches.
 */
public class Branch extends Command {
    public Branch(ArgumentParser parent) {
        super(parent);
    }

    @Override
    public ArgumentParser makeParser() {
        ArgumentParser parser = new ArgumentParser(getParent().getProgram() + " branch", getParent().getVersion(), "Manage branches interactively.");

        // ...

        return parser;
    }

    @Override
    public void execute(GitTool opts) {
        System.err.println("Branch!!");
    }
}
