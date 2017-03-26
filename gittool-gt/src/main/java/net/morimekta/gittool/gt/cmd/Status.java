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
import net.morimekta.console.args.Flag;
import net.morimekta.console.chr.Color;
import net.morimekta.gittool.gt.GitTool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.morimekta.console.chr.Color.BOLD;
import static net.morimekta.console.chr.Color.CLEAR;
import static net.morimekta.console.chr.Color.DIM;
import static net.morimekta.console.chr.Color.GREEN;
import static net.morimekta.console.chr.Color.RED;
import static net.morimekta.console.chr.Color.YELLOW;

/**
 * Interactively manage branches.
 */
public class Status extends Command {
    private static class FileStatus {
        DiffEntry staged;
        DiffEntry unstaged;

        FileStatus(DiffEntry un) {
            this.unstaged = un;
        }

        DiffEntry.ChangeType getOverallChange() {
            if (staged == null) {
                return unstaged.getChangeType();
            } else if (unstaged == null) {
                return staged.getChangeType();
            } else {
                if (unstaged.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    return DiffEntry.ChangeType.DELETE;
                }
                if (staged.getChangeType() == DiffEntry.ChangeType.ADD) {
                    return DiffEntry.ChangeType.ADD;
                }
                if (getNewestPath().equals(getOldestPath())) {
                    return DiffEntry.ChangeType.MODIFY;
                }
                // With if the file was copies at leat *once*, then i
                if (staged.getChangeType() == DiffEntry.ChangeType.COPY ||
                    unstaged.getChangeType() == DiffEntry.ChangeType.COPY) {
                    return DiffEntry.ChangeType.COPY;
                }
                return DiffEntry.ChangeType.RENAME;
            }
        }

        String stagedMod() {
            if (staged == null) {
                switch (unstaged.getChangeType()) {
                    case COPY:   return " C";
                    case DELETE: return " D";
                    case ADD:    return "??";  // untracked
                    case RENAME: return " R";
                    case MODIFY: return " M";
                    default:     return "  ";
                }
            } else if (unstaged == null) {
                switch (staged.getChangeType()) {
                    case COPY:   return "C ";
                    case DELETE: return "D ";
                    case ADD:    return "A ";
                    case RENAME: return "R ";
                    case MODIFY: return "  ";
                    default:     return "  ";
                }
            } else {
                // both...
                StringBuilder builder = new StringBuilder();
                switch (staged.getChangeType()) {
                    case COPY:   builder.append('C'); break;
                    case DELETE: builder.append('D'); break;
                    case ADD:    builder.append('A'); break;
                    case RENAME: builder.append('R'); break;
                    case MODIFY: builder.append('M'); break;
                    default:     builder.append(' '); break;
                }
                switch (unstaged.getChangeType()) {
                    case COPY:   builder.append('C'); break;
                    case DELETE: builder.append('D'); break;
                    case ADD:    builder.append('A'); break;
                    case RENAME: builder.append('R'); break;
                    case MODIFY: builder.append('M'); break;
                    default:     builder.append(' '); break;
                }
                return builder.toString();
            }
        }

        private boolean real(String path) {
            return path != null && !"/dev/null".equals(path);
        }

        String getOldestPath() {
            if (this.staged != null) {
                if (real(this.staged.getOldPath())) {
                    return this.staged.getOldPath();
                }
                return this.staged.getNewPath();
            }
            if (real(this.unstaged.getOldPath())) {
                return this.unstaged.getOldPath();
            }
            return this.unstaged.getNewPath();
        }

        String getNewestPath() {
            if (this.unstaged != null) {
                if (real(this.unstaged.getNewPath())) {
                    return this.unstaged.getNewPath();
                }
                return this.unstaged.getOldPath();
            }
            if (real(this.staged.getNewPath())) {
                return this.staged.getNewPath();
            }
            return this.staged.getOldPath();
        }

        String statusLine() {
            StringBuilder builder = new StringBuilder();
            builder.append(stagedMod());
            builder.append(' ');

            switch (getOverallChange()) {
                case ADD:
                    builder.append(new Color(GREEN, BOLD));
                    break;
                case DELETE:
                    builder.append(new Color(RED, BOLD));
                    break;
                case RENAME:
                case COPY:
                    builder.append(new Color(YELLOW, DIM));
                    break;
                case MODIFY:
                    builder.append(YELLOW);
                    break;
            }

            builder.append(getNewestPath());
            builder.append(CLEAR);

            if (!getNewestPath().equals(getOldestPath())) {
                builder.append(" <- ");
                builder.append(DIM);
                builder.append(getOldestPath());
                builder.append(CLEAR);
            }

            return builder.toString();
        }
    }

    private boolean files = false;
    private void setFiles(boolean files) {
        this.files = files;
    }

    public Status(ArgumentParser parent) {
        super(parent);
    }

    @Override
    public ArgumentParser makeParser() {
        ArgumentParser parser = new ArgumentParser(getParent().getProgram() + " diff", getParent().getVersion(), "File diff selection.");
        parser.add(new Flag("--files", "f", "Show file listing", this::setFiles));
        return parser;
    }

    private Optional<RevCommit> commitOf(Repository repository, String branch) throws IOException {
        try( RevWalk revWalk = new RevWalk(repository) ) {
            ObjectId oid = repository.resolve(refName(branch));
            revWalk.markStart(revWalk.parseCommit(oid));
            revWalk.sort(RevSort.COMMIT_TIME_DESC);
            return Optional.ofNullable(ImmutableList.copyOf(revWalk).get(0));
        }
    }

    private String refName(String branch) {
        if (remote(branch)) {
            return "refs/remotes/" + branch;
        } else {
            return "refs/heads/" + branch;
        }
    }

    private String date(RevCommit commit) {
        Clock clock = Clock.systemDefaultZone();
        ZonedDateTime instant = Instant.ofEpochSecond(commit.getCommitTime()).atZone(clock.getZone());
        ZonedDateTime midnight = Instant.now().atZone(clock.getZone()).withHour(0).withMinute(0).withSecond(0).withNano(0);

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

    private boolean remote(String branch) {
        // a/b is branch 'b' in remote 'a', so 'origin/master'...
        return branch.contains("/");
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
            String diffToBranch = gt.getDiffbase(currentBranch);

            Ref currentRef = repository.getRef("refs/heads/" + currentBranch);
            Ref diffToRef = remote(diffToBranch) ? repository.getRef("refs/remotes/" + diffToBranch) : repository.getRef("refs/heads/" + diffToBranch);

            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffToHead = diffToRef.getObjectId();

            // RevCommit currentCommit = commitOf(repository, currentHead);

            if (!currentHead.equals(diffToHead)) {
                String stats = "";
                String diff = remote(diffToBranch)
                              ? format("[->%s%s%s]", DIM, diffToBranch, CLEAR)
                              : format("[d:%s%s%s] ", CLR_BASE_BRANCH, diffToBranch, CLEAR);

                List<RevCommit> localCommits = ImmutableList.copyOf(git.log().addRange(diffToHead, currentHead).call());
                List<RevCommit> remoteCommits = ImmutableList.copyOf(git.log().addRange(currentHead, diffToHead).call());

                localCommits = Lists.reverse(localCommits);
                remoteCommits = Lists.reverse(remoteCommits);

                int adds = localCommits.size();
                int subs = remoteCommits.size();

                RevCommit ancestor, local;
                if (remoteCommits.size() > 0) {
                    List<RevCommit> sub2 = Lists.reverse(
                            ImmutableList.copyOf(
                                    git.log()
                                       .add(remoteCommits.get(0))
                                       .setMaxCount(2)
                                       .call()));
                    ancestor = sub2.get(0);
                } else {
                    ancestor = commitOf(repository, diffToBranch).orElseThrow(() -> new IOException("No commit in " + diffToBranch));
                }
                if (localCommits.size() > 0) {
                    local = localCommits.get(localCommits.size() - 1);
                } else {
                    local = commitOf(repository, currentBranch).orElseThrow(() -> new IOException("No commit in " + currentBranch));
                }

                if (adds > 0 || subs > 0) {
                    if (adds == 0) {
                        stats = format(" [%s-%d%s]", CLR_SUBS, subs, CLEAR);
                    } else if (subs == 0) {
                        stats = format(" [%s+%d%s]", CLR_ADDS, adds, CLEAR);
                    } else {
                        stats = format(" [%s+%d%s,%s-%d%s]", CLR_ADDS, adds, CLEAR, CLR_SUBS, subs, CLEAR);
                    }
                }

                System.out.println(format("Commits on %s%s%s%s since %s -- %s%s%s%s",
                                          CLR_UPDATED_BRANCH, currentBranch, CLEAR, stats, date(ancestor), diff, DIM, ancestor.getShortMessage(), CLEAR));

                if (files) {
                    ObjectReader reader = repository.newObjectReader();
                    CanonicalTreeParser ancestorTreeIter = new CanonicalTreeParser();
                    ancestorTreeIter.reset(reader, ancestor.getTree());
                    CanonicalTreeParser localTreeIter = new CanonicalTreeParser();
                    localTreeIter.reset(reader, local.getTree());

                    // finally get the list of changed files
                    List<DiffEntry> diffs = new Git(repository).diff()
                                                               .setOldTree(ancestorTreeIter)
                                                               .setNewTree(localTreeIter)
                                                               .call();
                    for (DiffEntry entry : diffs) {
                        switch (entry.getChangeType()) {
                            case RENAME:
                                System.out.println(format(" R %s%s%s <- %s%s%s",
                                                          new Color(YELLOW, DIM), entry.getNewPath(), CLEAR,
                                                          DIM, entry.getOldPath(), CLEAR));
                                break;
                            case MODIFY:
                                System.out.println(format("   %s", entry.getOldPath()));
                                break;
                            case ADD:
                                System.out.println(format(" A %s%s%s",
                                                          GREEN, entry.getNewPath(), CLEAR));
                                break;
                            case DELETE:
                                System.out.println(format(" D %s%s%s",
                                                          YELLOW, entry.getOldPath(), CLEAR));
                                break;
                            case COPY:
                                System.out.println(format(" C %s%s%s <- %s%s%s",
                                                          new Color(YELLOW, DIM), entry.getNewPath(), CLEAR,
                                                          DIM, entry.getOldPath(), CLEAR));
                                break;
                        }
                    }
                } else {
                    for (RevCommit localCommit : localCommits) {
                        System.out.println(format("+ %s%s%s (%s)", GREEN, localCommit.getShortMessage(), CLEAR, date(localCommit)));
                    }
                    for (RevCommit remoteCommit : remoteCommits) {
                        System.out.println(format("- %s%s%s (%s)", RED, remoteCommit.getShortMessage(), CLEAR, date(remoteCommit)));
                    }
                }
            } else {
                RevCommit diffToCommit = commitOf(repository, diffToBranch).orElseThrow(() -> new IOException("No commit in " + diffToBranch));
                System.out.println(format("No commits on %s%s%s since %s -- %s%s%s",
                                          GREEN, currentBranch, CLEAR, date(diffToCommit), DIM, diffToCommit.getShortMessage(), CLEAR));
            }

            // Check for staged and unstaged changes.
            if (files) {
                List<DiffEntry> staged = new Git(repository).diff()
                                                            .setCached(true)
                                                            .call();
                List<DiffEntry> unstaged = new Git(repository).diff()
                                                              .setCached(false)
                                                              .call();

                if (staged.size() > 0 || unstaged.size() > 0) {
                    System.out.println();
                    System.out.println(format("Uncommitted changes in %s%s%s:", CLR_UPDATED_BRANCH, currentBranch, CLEAR));

                    Map<String, FileStatus> st = unstaged.stream()
                                                       .map(FileStatus::new)
                                                       .collect(Collectors.toMap(
                            FileStatus::getNewestPath, fs -> fs));
                    staged.forEach(d -> {
                        if (d.getNewPath() != null) {
                            if (st.containsKey(d.getNewPath())) {
                                st.get(d.getNewPath()).staged = d;
                                return;
                            }
                        }
                        if (d.getOldPath() != null) {
                            if (st.containsKey(d.getOldPath())) {
                                st.get(d.getOldPath()).staged = d;
                                return;
                            }
                        }
                        FileStatus fs = new FileStatus(null);
                        fs.staged = d;

                        st.put(fs.getNewestPath(), fs);
                    });

                    for (FileStatus fs : new TreeMap<>(st).values()) {
                        System.out.println(fs.statusLine());
                    }
                }
            } else {
                List<DiffEntry> staged = new Git(repository).diff()
                                                            .setCached(true)
                                                            .call();
                List<DiffEntry> unstaged = new Git(repository).diff()
                                                              .setCached(false)
                                                              .call();

                if (staged.size() > 0 || unstaged.size() > 0) {
                    System.out.println(format("Uncommitted changes in %s%s%s:", CLR_UPDATED_BRANCH, currentBranch, CLEAR));

                    if (staged.size() > 0) {
                        long adds = staged.stream().filter(e -> e.getChangeType() == DiffEntry.ChangeType.ADD).count();
                        long dels = staged.stream().filter(e -> e.getChangeType() == DiffEntry.ChangeType.DELETE).count();
                        long mods = staged.size() - adds - dels;

                        System.out.print(format(" - %sStaged files%s   : ", new Color(YELLOW, DIM), CLEAR));
                        if (adds > 0) {
                            System.out.print(format(" %s+%d%s", GREEN, adds, CLEAR));
                        }
                        if (dels > 0) {
                            System.out.print(format(" %s-%d%s", RED, dels, CLEAR));
                        }
                        if (mods > 0) {
                            System.out.print(format(" %s/%d%s", DIM, mods, CLEAR));
                        }
                        System.out.println();
                    }
                    if (unstaged.size() > 0) {
                        long adds = unstaged.stream().filter(e -> e.getChangeType() == DiffEntry.ChangeType.ADD).count();
                        long dels = unstaged.stream().filter(e -> e.getChangeType() == DiffEntry.ChangeType.DELETE).count();
                        long mods = unstaged.size() - adds - dels;

                        System.out.print(format(" - %sUnstaged files%s :", YELLOW, CLEAR));
                        if (adds > 0) {
                            System.out.print(format(" %s+%d%s", GREEN, adds, CLEAR));
                        }
                        if (dels > 0) {
                            System.out.print(format(" %s-%d%s", RED, dels, CLEAR));
                        }
                        if (mods > 0) {
                            System.out.print(format(" %s/%d%s", DIM, mods, CLEAR));
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
