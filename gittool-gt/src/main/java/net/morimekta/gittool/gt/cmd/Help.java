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

import net.morimekta.console.args.Argument;
import net.morimekta.console.args.ArgumentParser;
import net.morimekta.console.args.SubCommandSet;
import net.morimekta.gittool.gt.GitTool;

/**
 * The 'usage' sub-command.
 */
public class Help extends Command {
    private final SubCommandSet<Command> subCommandSet;
    private String command;

    public Help(SubCommandSet<Command> subCommandSet, ArgumentParser parent) {
        super(parent);
        this.subCommandSet = subCommandSet;
    }

    @Override
    public ArgumentParser makeParser() {
        ArgumentParser parser = new ArgumentParser(getParent().getProgram() + " help", getParent().getVersion(), "Shows help.");

        parser.add(new Argument("command", "Show help for given command", this::setCommand));

        return parser;
    }

    private void setCommand(String s) {
        this.command = s;
    }

    @Override
    public void execute(GitTool opts) {
        if (command != null) {
            System.err.println("Help Command: " + command);
        } else {
            System.err.println("Help (general");
        }
    }
}
