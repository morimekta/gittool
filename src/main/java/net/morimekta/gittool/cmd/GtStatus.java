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

import net.morimekta.collect.UnmodifiableList;
import net.morimekta.file.FileUtil;
import net.morimekta.gittool.GitTool;
import net.morimekta.gittool.util.FileStatus;
import net.morimekta.strings.chr.Color;
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.morimekta.gittool.GitTool.pwd;
import static net.morimekta.strings.chr.Color.*;
import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Option.option;

/**
 * Interactively manage branches.
 */
public class GtStatus extends Command {
    private Path root;

    private boolean files = false;

    private void setFiles(boolean files) {
        this.files = files;
    }

    private boolean relative = false;

    private void setRelative(boolean relative) {
        this.relative = relative;
        this.files = true;
    }

    private String branch = null;

    private void setBranch(String branch) {
        this.branch = branch;
    }

    public GtStatus(ArgParser.Builder builder) {
        builder.add(option("--branch", "b", "Show status for branch", this::setBranch));
        builder.add(flag("--files", "f", "Show file listing", this::setFiles));
        builder.add(flag("--relative", "r", "Show relative path to PWD (implies --files)", this::setRelative));
    }

    private String date(RevCommit commit) {
        Clock clock = Clock.systemDefaultZone();
        ZonedDateTime instant = Instant.ofEpochSecond(commit.getCommitTime()).atZone(clock.getZone());
        ZonedDateTime midnight = Instant.now()
                .atZone(clock.getZone())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        if (instant.isBefore(midnight.minusDays(1))) {
            // before yesterday
            return DateTimeFormatter.ISO_LOCAL_DATE.format(instant);
        } else if (instant.isBefore(midnight)) {
            // yesterday, close enough so that time matter.
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant).replaceAll("[T]", " ");
        } else {
            return DateTimeFormatter.ISO_LOCAL_TIME.format(instant);
        }
    }

    private String path(String path) {
        if (relative) {
            return pwd.relativize(root.resolve(path)).toString();
        }
        return path;
    }

    @Override
    public void execute(GitTool gt) throws IOException {
        try (Repository repository = gt.getRepository()) {
            RepositoryState state = repository.getRepositoryState();
            if (state != RepositoryState.SAFE) {
                System.out.println(WARN + "Repository not in a safe state" + CLEAR + ": " + state.getDescription());
            }

            Git git = new Git(repository);
            String currentBranch = repository.getBranch();
            String diffWithBranch = branch != null ? branch : gt.getDiffbase(currentBranch);

            this.root = FileUtil.readCanonicalPath(gt.getRepositoryRoot());

            Ref currentRef = repository.getRefDatabase().findRef(gt.refName(currentBranch));
            Ref diffWithRef = repository.getRefDatabase().findRef(gt.refName(diffWithBranch));
            if (diffWithRef == null) {
                System.out.printf("No such branch %s%s%s%n", BOLD, diffWithBranch, CLEAR);
                return;
            }

            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffWithRef.getObjectId();

            if (!currentHead.equals(diffWithHead)) {
                String stats = "";
                String diff = gt.isRemote(diffWithBranch)
                        ? format("[->%s%s%s] ", DIM, diffWithBranch, CLEAR)
                        : format("[d:%s%s%s] ", CLR_BASE_BRANCH, diffWithBranch, CLEAR);

                UnmodifiableList<RevCommit> localCommits = UnmodifiableList.asList(
                        git.log().addRange(diffWithHead, currentHead).call());
                UnmodifiableList<RevCommit> remoteCommits = UnmodifiableList.asList(
                        git.log().addRange(currentHead, diffWithHead).call());

                localCommits = localCommits.reversed();
                remoteCommits = remoteCommits.reversed();

                int commits = localCommits.size();
                int missing = remoteCommits.size();

                RevCommit ancestor, local;
                if (!remoteCommits.isEmpty()) {
                    List<RevCommit> sub2 = UnmodifiableList.asList(
                            git.log()
                                    .add(remoteCommits.get(0))
                                    .setMaxCount(2)
                                    .call()).reversed();
                    ancestor = sub2.get(0);
                } else {
                    ancestor = gt.commitOf(diffWithBranch)
                            .orElseThrow(() -> new IOException("No commit on " + diffWithBranch));
                }
                if (!localCommits.isEmpty()) {
                    local = localCommits.get(localCommits.size() - 1);
                } else {
                    local = gt.commitOf(currentBranch)
                            .orElseThrow(() -> new IOException("No commit on " + currentBranch));
                }

                if (commits > 0 || missing > 0) {
                    if (commits == 0) {
                        stats = format(" [%s-%d%s]", CLR_SUBS, missing, CLEAR);
                    } else if (missing == 0) {
                        stats = format(" [%s+%d%s]", CLR_ADDS, commits, CLEAR);
                    } else {
                        stats = format(" [%s+%d%s,%s-%d%s]", CLR_ADDS, commits, CLEAR, CLR_SUBS, missing, CLEAR);
                    }
                }

                System.out.printf("Commits on %s%s%s%s since %s -- %s%s%s%s%n",
                        CLR_UPDATED_BRANCH,
                        currentBranch,
                        CLEAR,
                        stats,
                        date(ancestor),
                        diff,
                        DIM,
                        ancestor.getShortMessage(),
                        CLEAR);

                if (files) {
                    ObjectReader reader = repository.newObjectReader();
                    CanonicalTreeParser ancestorTreeIter = new CanonicalTreeParser();
                    ancestorTreeIter.reset(reader, ancestor.getTree());
                    CanonicalTreeParser localTreeIter = new CanonicalTreeParser();
                    localTreeIter.reset(reader, local.getTree());

                    // finally get the list of changed files
                    List<DiffEntry> diffs = git.diff()
                            .setShowNameAndStatusOnly(true)
                            .setOldTree(ancestorTreeIter)
                            .setNewTree(localTreeIter)
                            .call();
                    for (DiffEntry entry : diffs) {
                        switch (entry.getChangeType()) {
                            case RENAME:
                                System.out.printf(" R %s%s%s <- %s%s%s%n",
                                        new Color(YELLOW, DIM), entry.getNewPath(), CLEAR,
                                        DIM, path(entry.getOldPath()), CLEAR);
                                break;
                            case MODIFY:
                                System.out.printf("   %s%n", path(entry.getOldPath()));
                                break;
                            case ADD:
                                System.out.printf(" A %s%s%s%n",
                                        GREEN, path(entry.getNewPath()), CLEAR);
                                break;
                            case DELETE:
                                System.out.printf(" D %s%s%s%n",
                                        YELLOW, path(entry.getOldPath()), CLEAR);
                                break;
                            case COPY:
                                System.out.printf(" C %s%s%s <- %s%s%s%n",
                                        new Color(YELLOW, DIM), path(entry.getNewPath()), CLEAR,
                                        DIM, path(entry.getOldPath()), CLEAR);
                                break;
                        }
                    }
                } else {
                    for (RevCommit localCommit : localCommits) {
                        System.out.printf("+ %s%s%s (%s)%n",
                                GREEN,
                                localCommit.getShortMessage(),
                                CLEAR,
                                date(localCommit));
                    }
                    for (RevCommit remoteCommit : remoteCommits) {
                        System.out.printf("- %s%s%s (%s)%n",
                                RED,
                                remoteCommit.getShortMessage(),
                                CLEAR,
                                date(remoteCommit));
                    }
                }
            } else {
                RevCommit diffWithCommit = gt.commitOf(diffWithBranch)
                        .orElseThrow(() -> new IOException("No commit in " + diffWithBranch));
                System.out.printf("No commits on %s%s%s since %s -- %s%s%s%n",
                        GREEN,
                        currentBranch,
                        CLEAR,
                        date(diffWithCommit),
                        DIM,
                        diffWithCommit.getShortMessage(),
                        CLEAR);
            }

            // Check for staged and unstaged changes.
            if (files) {
                List<DiffEntry> staged = git.diff()
                        .setCached(true)
                        .call();
                List<DiffEntry> unstaged = git.diff()
                        .setCached(false)
                        .call();

                if (!staged.isEmpty() || !unstaged.isEmpty()) {
                    System.out.println();
                    System.out.printf("%sUncommitted%s changes on %s%s%s:%n",
                            RED,
                            CLEAR,
                            CLR_UPDATED_BRANCH,
                            currentBranch,
                            CLEAR);

                    Map<String, FileStatus> st = unstaged.stream()
                            .map(d -> new FileStatus(relative, root, d))
                            .collect(Collectors.toMap(
                                    FileStatus::getNewestPath, fs -> fs));
                    staged.forEach(d -> {
                        if (d.getNewPath() != null) {
                            if (st.containsKey(d.getNewPath())) {
                                st.get(d.getNewPath()).setStaged(d);
                                return;
                            }
                        }
                        if (d.getOldPath() != null) {
                            if (st.containsKey(d.getOldPath())) {
                                st.get(d.getOldPath()).setStaged(d);
                                return;
                            }
                        }
                        FileStatus fs = new FileStatus(relative, root, null).setStaged(d);

                        st.put(fs.getNewestPath(), fs);
                    });

                    for (FileStatus fs : new TreeMap<>(st).values()) {
                        System.out.println(fs.statusLine());
                    }
                }
            } else {
                List<DiffEntry> staged = git.diff()
                        .setShowNameAndStatusOnly(true)
                        .setCached(true)
                        .call();
                List<DiffEntry> unstaged = git.diff()
                        .setShowNameAndStatusOnly(true)
                        .setCached(false)
                        .call();

                if (!staged.isEmpty() || !unstaged.isEmpty()) {
                    System.out.printf("Uncommitted changes on %s%s%s:%n",
                            CLR_UPDATED_BRANCH,
                            currentBranch,
                            CLEAR);

                    if (!staged.isEmpty()) {
                        long adds = staged.stream().filter(e -> e.getChangeType() == DiffEntry.ChangeType.ADD).count();
                        long dels = staged.stream()
                                .filter(e -> e.getChangeType() == DiffEntry.ChangeType.DELETE)
                                .count();
                        long mods = staged.size() - adds - dels;

                        System.out.printf(" - %sStaged files%s   :", new Color(YELLOW, DIM), CLEAR);
                        if (adds > 0) {
                            System.out.printf(" %s+%d%s", GREEN, adds, CLEAR);
                        }
                        if (dels > 0) {
                            System.out.printf(" %s-%d%s", RED, dels, CLEAR);
                        }
                        if (mods > 0) {
                            System.out.printf(" %s/%d%s", DIM, mods, CLEAR);
                        }
                        System.out.println();
                    }
                    if (!unstaged.isEmpty()) {
                        long adds = unstaged.stream()
                                .filter(e -> e.getChangeType() == DiffEntry.ChangeType.ADD)
                                .count();
                        long dels = unstaged.stream()
                                .filter(e -> e.getChangeType() == DiffEntry.ChangeType.DELETE)
                                .count();
                        long mods = unstaged.size() - adds - dels;

                        System.out.printf(" - %sUnstaged files%s :", YELLOW, CLEAR);
                        if (adds > 0) {
                            System.out.printf(" %s+%d%s", GREEN, adds, CLEAR);
                        }
                        if (dels > 0) {
                            System.out.printf(" %s-%d%s", RED, dels, CLEAR);
                        }
                        if (mods > 0) {
                            System.out.printf(" %s/%d%s", DIM, mods, CLEAR);
                        }
                        System.out.println();
                    }
                }
            }
        } catch (GitAPIException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
