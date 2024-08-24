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
package net.morimekta.gittool;

import net.morimekta.collect.UnmodifiableList;
import net.morimekta.gittool.cmd.Command;
import net.morimekta.gittool.cmd.GtBranch;
import net.morimekta.gittool.cmd.GtHelp;
import net.morimekta.gittool.cmd.GtStatus;
import net.morimekta.gittool.util.Utils;
import net.morimekta.io.tty.TTY;
import net.morimekta.terminal.args.ArgException;
import net.morimekta.terminal.args.ArgHelp;
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Flag.flagLong;
import static net.morimekta.terminal.args.Option.optionLong;
import static net.morimekta.terminal.args.SubCommand.subCommand;
import static net.morimekta.terminal.args.ValueParser.dir;

public class GitTool {
    private static final String DOT_GIT = ".git";
    public final         TTY    tty;

    public ArgParser parser = null;

    private Command command = null;
    private boolean help    = false;
    private boolean version = false;
    private boolean verbose = false;

    private File       repositoryRoot = null;
    private Repository repository     = null;

    private final Map<String, String> env;
    public static Path                pwd;

    protected GitTool(TTY tty, Map<String, String> env) {
        this.tty = tty;
        this.env = env;
        pwd = Paths.get(env.get("PWD")).normalize().toAbsolutePath();
    }

    private void setRepositoryRoot(Path git_root) {
        this.repositoryRoot = git_root.toFile();
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
            File current = new File(env.get("PWD")).getCanonicalFile().getAbsoluteFile();
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

    private ArgParser makeParser() {
        return ArgParser
                .argParser("gt",
                           Utils.versionString(),
                           "Extra git tools by morimekta")
                .add(optionLong("--git_repository",
                                "The git repository root directory",
                                dir(this::setRepositoryRoot)))
                .add(flag("--help", "h?", "Show help", this::setHelp))
                .add(flag("--version", "V", "Show program version", this::setVersion))
                .add(flagLong("--verbose", "Show verbose exceptions", this::setVerbose))
                .withSubCommands("cmd", "", this::setCommand)
                .add(subCommand("help", "Show help", GtHelp::new).alias("h"))
                .add(subCommand("branch", "Change branch", parser -> new GtBranch(parser)).alias("br", "b"))
                .add(subCommand("status", "Review branch status", GtStatus::new).alias("st"))
                .build();
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
        String remote = getRemote(branch);
        if (remote != null) {
            return remote;
        }
        return getDefaultBranch();
    }

    public String getRemote(String branch) {
        String remote = repository.getConfig().getString("branch", branch, "remote");
        if (remote != null) {
            String ref = repository.getConfig().getString("branch", branch, "merge");
            if (ref != null) {
                if (ref.startsWith("refs/heads/")) {
                    return remote + "/" + ref.substring(11);
                }
            }
        }
        return null;
    }

    public Optional<RevCommit> commitOf(Repository repository, String branch) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId oid = repository.resolve(refName(branch));
            revWalk.markStart(revWalk.parseCommit(oid));
            revWalk.sort(RevSort.COMMIT_TIME_DESC);
            return Optional.ofNullable(UnmodifiableList.asList(revWalk).get(0));
        }
    }

    public boolean isRemote(String branch) {
        // a/b is branch 'b' in remote 'a', so 'origin/master'...
        return branch.contains("/");
    }

    public String refName(String branch) {
        if (isRemote(branch)) {
            return "refs/remotes/" + branch;
        } else {
            return "refs/heads/" + branch;
        }
    }

    public void execute(String... args) {
        Locale.setDefault(Locale.US);  // just for the record.

        parser = makeParser();

        try {
            parser.parse(args);

            if (showVersion()) {
                System.out.println(parser.getVersion());
                return;
            } else if (showHelp()) {
                ArgHelp.argHelp(parser).printHelp(System.out);
                return;
            }

            command.execute(this);
            return;
        } catch (ArgException e) {
            System.err.println("Argument Error: " + e.getMessage());
            System.err.println();
            ArgHelp.argHelp(parser).printHelp(System.err);
            if (verbose) {
                System.err.println();
                e.printStackTrace(System.err);
            }
        } catch (GitAPIException e) {
            System.err.println("Git Error: " + e.getMessage());
            if (verbose) {
                System.err.println();
                e.printStackTrace(System.err);
            }
        } catch (IOException | UncheckedIOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            if (verbose) {
                System.err.println();
                e.printStackTrace(System.err);
            }
        } catch (Exception e) {
            System.err.println("Internal Error: " + e.getMessage());
            if (verbose) {
                System.err.println();
                e.printStackTrace(System.err);
            }
        }

        errorExit();
    }

    protected void errorExit() {
        System.exit(1);
    }

    public static void main(String... args) {
        new GitTool(new TTY(), System.getenv()).execute(args);
    }
}
