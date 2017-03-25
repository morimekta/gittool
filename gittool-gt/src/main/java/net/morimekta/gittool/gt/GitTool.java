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
package net.morimekta.gittool.gt;

import net.morimekta.console.args.ArgumentException;
import net.morimekta.console.args.ArgumentParser;
import net.morimekta.console.args.Flag;
import net.morimekta.console.args.Option;
import net.morimekta.console.args.SubCommand;
import net.morimekta.console.args.SubCommandSet;
import net.morimekta.gittool.common.Utils;
import net.morimekta.gittool.gt.cmd.Branch;
import net.morimekta.gittool.gt.cmd.Command;
import net.morimekta.gittool.gt.cmd.Help;

import java.io.File;
import java.io.IOException;

import static net.morimekta.console.util.Parser.dir;

public class GitTool {
    private File root = null;
    private Command command = null;
    private boolean help = false;
    private boolean version = false;
    private boolean verbose = false;

    private void setRoot(File root) {
        this.root = root;
    }

    private void setCommand(Command command) {
        this.command = command;
    }

    private void setHelp(boolean help) {
        this.help = help;
    }

    private void setVersion(boolean version) {
        this.version = version;
    }

    private void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public File getRoot() throws IOException {
        if (root == null) {
            File current = new File(".").getCanonicalFile().getAbsoluteFile();
            while (!(new File(current, ".git")).exists()) {
                current = current.getParentFile();
                if (current == null) {
                    throw new ArgumentException("Not in a git repository!");
                }
            }
            root = current;
        }
        return root;
    }

    public File getDotGt() throws IOException {
        return new File(getRoot(), ".gt");
    }

    public Command getCommand() {
        return command;
    }

    public boolean showHelp() {
        return (help || command == null);
    }

    public static void main(String... args) {
        ArgumentParser parser = new ArgumentParser("gt",
                                                   Utils.versionString(),
                                                   "Extra git tools by morimekta");
        GitTool gt = new GitTool();
        try {
            parser.add(new Option("--root", null, "DIR", "The git root directory", dir(gt::setRoot)));
            parser.add(new Flag("--help", "h?", "Show help", gt::setHelp, null, true));
            parser.add(new Flag("--version", "V", "Show program version", gt::setVersion));
            parser.add(new Flag("--verbose", null, "Show verbose exceptions", gt::setVerbose, null, true));

            SubCommandSet<Command> subCommandSet = new SubCommandSet<>("cmd", "", gt::setCommand);
            subCommandSet.add(new SubCommand<>("help", "Show help", false, () -> new Help(subCommandSet, parser), Command::makeParser, "h"));
            subCommandSet.add(new SubCommand<>("branch", "Change and manage branches", false, () -> new Branch(parser), Command::makeParser, "b", "br"));
            parser.add(subCommandSet);
            parser.parse(args);

            if (gt.showVersion()) {
                System.out.println(parser.getVersion());
                return;
            } else if (gt.showHelp()) {
                System.out.println(parser.getProgramDescription());
                System.out.println("Usage: " + parser.getSingleLineUsage());
                System.out.println();
                parser.printUsage(System.out);
                System.out.println();
                System.out.println("Available Commands:");
                System.out.println();
                subCommandSet.printUsage(System.out);
                return;
            }

            gt.execute();
            return;
        } catch (ArgumentException e) {
            System.err.println("Argument Error: " + e.getMessage());
            System.err.println();
            System.err.println("Usage: " + parser.getSingleLineUsage());
            System.err.println();
            parser.printUsage(System.err);
            if (gt.verbose) {
                System.err.println();
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            if (gt.verbose) {
                System.err.println();
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    private boolean showVersion() {
        return version;
    }

    private void execute() throws Exception {
        command.execute(this);
    }
}
