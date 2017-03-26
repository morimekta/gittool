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
import net.morimekta.gittool.gt.cmd.Status;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

import static net.morimekta.console.util.Parser.dir;

public class GitTool {
    private static final String DOT_GIT = ".git";

    private SubCommandSet<Command> subCommandSet = null;

    private Command command = null;
    private boolean help    = false;
    private boolean version = false;
    private boolean verbose = false;

    private File       repositoryRoot = null;
    private Repository repository     = null;

    private void setRepositoryRoot(File git_root) {
        this.repositoryRoot = git_root;
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

    public File getRepositoryRoot() throws IOException {
        if (repositoryRoot == null) {
            File current = new File(".").getCanonicalFile().getAbsoluteFile();
            while (!(new File(current, DOT_GIT)).exists()) {
                current = current.getParentFile();
                if (current == null) {
                    throw new IOException("Not in a git repository!");
                }
            }
            repositoryRoot = current;
        }
        return repositoryRoot;
    }

    public Repository getRepository() throws IOException {
        if (repository == null) {
            repository = new FileRepositoryBuilder()
                    .setGitDir(new File(getRepositoryRoot(), DOT_GIT))
                    .build();
        }
        return repository;
    }

    public boolean showHelp() {
        return (help || command == null);
    }

    private boolean showVersion() {
        return version && !help;
    }

    private SubCommandSet<Command> getSubCommandSet() {
        return subCommandSet;
    }

    private ArgumentParser makeParser() {
        ArgumentParser parser = new ArgumentParser("gt",
                                                   Utils.versionString(),
                                                   "Extra git tools by morimekta");

        parser.add(new Option("--git_repository", null, "DIR", "The git repository root directory", dir(this::setRepositoryRoot)));
        parser.add(new Flag("--help", "h?", "Show help", this::setHelp, null, true));
        parser.add(new Flag("--version", "V", "Show program version", this::setVersion));
        parser.add(new Flag("--verbose", null, "Show verbose exceptions", this::setVerbose, null, true));

        subCommandSet = new SubCommandSet<>("cmd", "", this::setCommand);
        subCommandSet.add(new SubCommand<>("help", "Show help", false, () -> new Help(subCommandSet, parser), Command::makeParser, "h"));
        subCommandSet.add(new SubCommand<>("branch", "Change and manage branches", false, () -> new Branch(parser), Command::makeParser, "b", "br"));
        subCommandSet.add(new SubCommand<>("status", "Review branch status", false, () -> new Status(parser), Command::makeParser, "st"));
        parser.add(subCommandSet);

        return parser;
    }

    private static final String MASTER = "master";

    public String getDefaultBranch() {
        String tmp = repository.getConfig().getString("default", null, "branch");
        if (tmp != null) {
            return tmp;
        }
        return MASTER;
    }

    public String getDiffbase(String branch) {
        String tmp = repository.getConfig().getString("branch", branch, "diffbase");
        if (tmp != null) {
            return tmp;
        }
        return getDefaultBranch();
    }

    public static void main(String... args) {
        Locale.setDefault(Locale.US);  // just for the record.

        GitTool gt = new GitTool();
        ArgumentParser parser = gt.makeParser();

        try {
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
                gt.getSubCommandSet().printUsage(System.out);
                return;
            }

            gt.command.execute(gt);
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
        } catch (IOException | UncheckedIOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            if (gt.verbose) {
                System.err.println();
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Unhandled exception: " + e.getMessage());
            if (gt.verbose) {
                System.err.println();
                e.printStackTrace();
            }
        }

        System.exit(1);
    }
}
