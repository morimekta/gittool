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

import net.morimekta.console.args.Argument;
import net.morimekta.console.args.ArgumentParser;
import net.morimekta.console.args.SubCommandSet;
import net.morimekta.gittool.GitTool;

import static net.morimekta.console.chr.Color.BOLD;
import static net.morimekta.console.chr.Color.CLEAR;

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
            System.out.println("Usage: " + subCommandSet.getSingleLineUsage(command));
            switch (command) {
                case "branch":
                    System.out.println();
                    System.out.println(BOLD + "Show branches and manage them interactively" + CLEAR);
                    System.out.println();
                    System.out.println("Possible commands on a branch:");
                    System.out.println(" - <enter>: Check out branch.");
                    System.out.println(" - 'd': Set diff base for gt on the branch.");
                    System.out.println(" - 'm': Move (rename) branch.");
                    System.out.println(" - 'D': Delete selected branch.");
                    System.out.println(" - 'q': Exit to console.");
                    System.out.println();
                    System.out.println("Branch line legend:");
                    System.out.println(" 1 * master  <- origin/master -- MOD --");
                    System.out.println(" 2   develop :d master [+1,-2]");
                    System.out.println();
                    System.out.println(" \"1\": The branch index. The default branch is always sorted first");
                    System.out.println(" \"*\": The asterisk marks the current checked out branch");
                    System.out.println(" \"<- [remote/branch]\": The branch is tracking this remote");
                    System.out.println(" \"d: [branch]\": The branch has this diff base");
                    System.out.println(" \"[+1,-2]\": Commits only on this branch, and only on compared branch");
                    System.out.println(" \"-- MOD --\": If current branch has uncommitted files");
                    break;
                case "status":
                    System.out.println();
                    System.out.println(BOLD + "Show current branch status" + CLEAR);
                    System.out.println();
                    subCommandSet.printUsage(System.out, command);
                    break;
                case "help":
                    System.out.println();
                    System.out.println(BOLD + "Show help information" + CLEAR);
                    subCommandSet.printUsage(System.out, command);
                    break;
                default:
                    System.out.println();
                    subCommandSet.printUsage(System.out, command);
                    break;
            }
        } else {
            ArgumentParser parser = getParent();
            System.out.println(parser.getProgramDescription());
            System.out.println("Usage: " + parser.getSingleLineUsage());
            System.out.println();
            parser.printUsage(System.out);
            System.out.println();
            System.out.println("Available Commands:");
            System.out.println();
            subCommandSet.printUsage(System.out);
        }
    }
}
