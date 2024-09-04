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
import net.morimekta.terminal.args.ArgParser;

import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.terminal.args.ArgHelp.argHelp;
import static net.morimekta.terminal.args.Argument.argument;

/**
 * The 'usage' sub-command.
 */
public class GtHelp extends Command {
    private String command;

    public GtHelp(ArgParser.Builder builder) {
        builder.add(argument("command", "Show help for given command", this::setCommand));
    }

    private void setCommand(String s) {
        this.command = s;
    }

    @Override
    public void execute(GitTool opts) {
        var parent = opts.parser;
        var subCommandSet = parent.getSubCommandSet();

        if (command != null) {
            switch (command) {
                case "b":
                case "br":
                case "branch":
                    System.out.println(BOLD + "Show branches and manage them interactively" + CLEAR);
                    System.out.println();
                    System.out.println("Possible commands on a branch:");
                    System.out.println(" - <enter>: Check out branch.");
                    System.out.println(" - 'm': Move (rename) branch.");
                    System.out.println(" - 'b': Set diff base for gt on the branch.");
                    System.out.println(" - 'D': Delete selected branch.");
                    System.out.println(" - 'q': Exit to console.");
                    System.out.println();
                    System.out.println("Branch line legend:");
                    System.out.println(" 1 * master  -- MOD -- -> origin/master");
                    System.out.println(" 2   change  [+1] gone: origin/change");
                    System.out.println(" 3   develop [+1,-2]");
                    System.out.println();
                    System.out.println(" \"1\": The branch index. The default branch is always sorted first");
                    System.out.println(" \"*\": The asterisk marks the current checked out branch");
                    System.out.println(" \"<- [remote/branch]\": The branch is tracking this remote");
                    System.out.println(" \"d: [branch]\": The branch has this diff base");
                    System.out.println(" \"[+1,-2]\": Commits only on this branch, and only on compared branch");
                    System.out.println(" \"-- MOD --\": If current branch has uncommitted files");
                    System.out.println();
                    argHelp(subCommandSet.parserForSubCommand("branch")).printHelp(System.out);
                    break;
                case "st":
                case "status":
                    System.out.println(BOLD + "Show current branch status" + CLEAR);
                    System.out.println();
                    argHelp(subCommandSet.parserForSubCommand("status")).printHelp(System.out);
                    break;
                case "d":
                case "diff":
                    System.out.println(BOLD + "Show branch diff" + CLEAR);
                    System.out.println();
                    argHelp(subCommandSet.parserForSubCommand("diff")).printHelp(System.out);
                    break;
                case "l":
                case "log":
                    System.out.println(BOLD + "Show commit log" + CLEAR);
                    System.out.println();
                    argHelp(subCommandSet.parserForSubCommand("log")).printHelp(System.out);
                    break;
                case "help":
                    System.out.println(BOLD + "Show help information" + CLEAR);
                    System.out.println();
                    argHelp(subCommandSet.parserForSubCommand("help")).printHelp(System.out);
                    break;
                default:
                    System.err.println("Unknown command: '" + command + "'");
                    System.err.println();
                    argHelp(parent).showHidden(false)
                                   .showSubCommands(true)
                                   .showHiddenSubCommands(true)
                                   .printHelp(System.err);
                    break;
            }
        } else {
            argHelp(parent)
                    .showHidden(false)
                    .showSubCommands(true)
                    .printHelp(System.out);
        }
    }
}
