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
import net.morimekta.collect.UnmodifiableSet;
import net.morimekta.collect.util.LazyCachedSupplier;
import net.morimekta.gittool.cmd.Command;
import net.morimekta.gittool.cmd.GtBranch;
import net.morimekta.gittool.cmd.GtDiff;
import net.morimekta.gittool.cmd.GtHelp;
import net.morimekta.gittool.cmd.GtStatus;
import net.morimekta.gittool.util.Utils;
import net.morimekta.io.tty.TTY;
import net.morimekta.terminal.args.ArgException;
import net.morimekta.terminal.args.ArgHelp;
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.morimekta.collect.util.LazyCachedSupplier.lazyCache;
import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Flag.flagLong;
import static net.morimekta.terminal.args.Option.optionLong;
import static net.morimekta.terminal.args.SubCommand.subCommand;
import static net.morimekta.terminal.args.ValueParser.dir;

public class GitTool {
    private static final String DOT_GIT = ".git";
    public static        Path   pwd;

    public final TTY tty;

    public ArgParser parser = null;

    private Command command = null;
    private boolean help    = false;
    private boolean version = false;
    private boolean verbose = false;

    private Path         repositoryRoot = null;
    private Repository   repository     = null;
    private StoredConfig config         = null;
    private Git          git            = null;

    private final Map<String, String> env;

    protected GitTool(TTY tty, Map<String, String> env) {
        this.tty = tty;
        this.env = env;
        pwd = Paths.get(env.get("PWD")).normalize().toAbsolutePath();
    }

    private void setRepositoryRoot(Path git_root) {
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

    public Path getRepositoryRoot() throws IOException {
        if (repositoryRoot == null) {
            var current = pwd;
            while (!Files.exists(current.resolve(DOT_GIT))) {
                current = current.getParent();
                if (current == null || current.toString().isEmpty()) {
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
                    .setGitDir(getRepositoryRoot().resolve(DOT_GIT).toFile())
                    .build();
        }
        return repository;
    }

    public Git getGit() throws IOException {
        if (git == null) {
            git = new Git(getRepository());
        }
        return git;
    }

    public StoredConfig getConfig() throws IOException {
        if (config == null) {
            config = getRepository().getConfig();
            try {
                config.load();
            } catch (ConfigInvalidException e) {
                throw new IOException(e);
            }
        }
        return config;
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
                .add(subCommand("branch", "Change branch", parser -> new GtBranch()).alias("br", "b"))
                .add(subCommand("status", "Review branch status", GtStatus::new).alias("st"))
                .add(subCommand("diff", "Diff changes", GtDiff::new).alias("d"))
                .build();
    }

    private static final Set<String> MASTER_OPTS = Set.of(
            "master", "develop", "main");

    public LazyCachedSupplier<String> defaultBranch = lazyCache(() -> {
        try {
            var cfg = getConfig();
            String tmp = cfg.getString("default", null, "branch");
            if (tmp != null) {
                return tmp;
            }
            var branches = getGit().branchList().call();
            for (var branch : branches) {
                var name = branch.getName().replaceAll("^refs/heads/", "");
                if (MASTER_OPTS.contains(name)) {
                    return name;
                }
            }
            // Otherwise just return first.
            return branches.get(0).getName().replaceAll("^refs/heads/", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    });

    public LazyCachedSupplier<Set<String>> remoteNames = lazyCache(() -> {
        try {
            return getGit().remoteList()
                           .call()
                           .stream()
                           .map(RemoteConfig::getName)
                           .sorted()
                           .collect(UnmodifiableSet.toSet());
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException(e);
        }
    });

    public LazyCachedSupplier<Boolean> hasUncommitted = lazyCache(() -> {
        try {
            var hasCached = !getGit()
                    .diff()
                    .setShowNameAndStatusOnly(true)
                    .setCached(true)
                    .call().isEmpty();
            var hasUncached = getGit()
                    .diff()
                    .setShowNameAndStatusOnly(true)
                    .setCached(false)
                    .call()
                    .stream()
                    .anyMatch(i -> {
                        if (i.getChangeType() == DiffEntry.ChangeType.DELETE) {
                            try {
                                var path = getRepositoryRoot().resolve(i.getOldPath());
                                if (Files.isDirectory(path)) {
                                    // Weirdness where it reports empty folders that are checked
                                    // in as a folder as deleted.
                                    return false;
                                }
                            } catch (IOException e) {
                                return true;
                            }
                        }
                        return true;
                    });

            return hasCached || hasUncached;
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
    });

    public boolean isRemote(String branch) {
        // a/b is branch 'b' in remote 'a', so 'origin/master'...
        if (branch.contains("/")) {
            var opt = branch.substring(0, branch.indexOf('/'));
            return remoteNames.get().contains(opt);
        }
        return false;
    }

    public RevCommit lastCommonAncestor(
            RevCommit baseCommit,
            RevCommit targetCommit) throws IOException, GitAPIException {
        UnmodifiableList<RevCommit> remoteCommits = UnmodifiableList.asList(
                getGit().log().addRange(targetCommit, baseCommit).call());
        remoteCommits = remoteCommits.reversed();
        if (!remoteCommits.isEmpty()) {
            // Get the most recent commit before the oldest commit in the remote-only list.
            List<RevCommit> sub2 = UnmodifiableList.asList(
                    getGit().log()
                            .add(remoteCommits.get(0))
                            .setMaxCount(2)
                            .call()).reversed();
            return sub2.get(0);
        } else {
            // It is the base commit.
            return baseCommit;
        }
    }

    public record Log(List<RevCommit> local, List<RevCommit> remote) {}

    public Log log(ObjectId baseIOD, ObjectId targetIOD) throws IOException, GitAPIException {
        UnmodifiableList<RevCommit> localCommits = UnmodifiableList.asList(
                getGit().log().addRange(baseIOD, targetIOD).call());
        UnmodifiableList<RevCommit> remoteCommits = UnmodifiableList.asList(
                getGit().log().addRange(targetIOD, baseIOD).call());
        return new Log(localCommits.reversed(), remoteCommits.reversed());
    }

    public List<DiffEntry> diff(RevCommit baseRev, RevCommit targetRev) throws IOException, GitAPIException {
        try (var reader = repository.newObjectReader()) {
            var baseTreeIter = new CanonicalTreeParser();
            baseTreeIter.reset(reader, baseRev.getTree());
            var targetTreeIter = new CanonicalTreeParser();
            targetTreeIter.reset(reader, targetRev.getTree());

            // finally get the list of changed files
            return git.diff()
                      .setShowNameAndStatusOnly(true)
                      .setOldTree(baseTreeIter)
                      .setNewTree(targetTreeIter)
                      .call();
        }
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

            try {
                command.execute(this);
            } finally {
                var g = git;
                if (g != null) {
                    g.close();
                }

                var repo = repository;
                if (repo != null) {
                    repo.close();
                }
            }
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
