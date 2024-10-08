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

import net.morimekta.file.FileUtil;
import net.morimekta.gittool.GitTool;
import net.morimekta.gittool.util.BranchInfo;
import net.morimekta.gittool.util.FileStatus;
import net.morimekta.gittool.util.Utils;
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.morimekta.gittool.GitTool.pwd;
import static net.morimekta.gittool.util.Colors.YELLOW_BOLD;
import static net.morimekta.gittool.util.Colors.YELLOW_DIM;
import static net.morimekta.gittool.util.Utils.addsAndDeletes;
import static net.morimekta.gittool.util.Utils.date;
import static net.morimekta.strings.StringUtil.clipWidth;
import static net.morimekta.strings.chr.Color.BLUE;
import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;
import static net.morimekta.strings.chr.Color.YELLOW;
import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Option.option;

/**
 * Interactively manage branches.
 */
public class GtStatus extends Command {
    private Path root;

    private boolean relative = false;
    private String  branch   = null;

    private void setBranch(String branch) {
        this.branch = branch;
    }

    public GtStatus(ArgParser.Builder builder) {
        builder.add(option("--branch", "b", "Show status for branch", this::setBranch));
        builder.add(flag("--relative", "r", "Show relative path to PWD", b -> relative = b));
    }

    private String path(String path) {
        if (relative) {
            return pwd.relativize(root.resolve(path)).toString();
        }
        return path;
    }

    @Override
    public void execute(GitTool gt) throws IOException {
        try {
            var width = gt.terminalWidth();

            Repository repository = gt.getRepository();
            RepositoryState state = repository.getRepositoryState();
            if (state != RepositoryState.SAFE) {
                System.out.println(clipWidth(
                        YELLOW_BOLD
                        + "Repository not in a safe state"
                        + CLEAR
                        + ": "
                        + state.getDescription(), width));
            }

            var git = gt.getGit();
            this.root = FileUtil.readCanonicalPath(gt.getRepositoryRoot());

            var currentBranch = repository.getBranch();
            var currentRef = repository.getRefDatabase().findRef(gt.refName(currentBranch));
            var current = new BranchInfo(currentRef, gt);

            String diffWithBranch = branch != null ? branch : current.diffBase();
            Ref diffWithRef = repository.getRefDatabase().findRef(gt.refName(diffWithBranch));
            if (diffWithRef == null) {
                System.out.println(clipWidth(
                        "No such branch %s%s%s".formatted(BOLD, diffWithBranch, CLEAR), width));
                return;
            }
            String diff = gt.isRemote(diffWithBranch)
                          ? format("->%s%s%s", BLUE, diffWithBranch, CLEAR)
                          : format("d:%s%s%s", YELLOW_DIM, diffWithBranch, CLEAR);

            var diffWith = new BranchInfo(diffWithRef, gt);

            if (!current.commit().equals(diffWith.commit())) {
                var log = gt.log(diffWith.commit(), current.commit());
                int commits = log.local().size();
                int missing = log.remote().size();
                String stats = "";
                if (commits > 0 || missing > 0) {
                    stats = " " + addsAndDeletes(commits, missing, null);
                }

                var ancestor = gt.lastCommonAncestor(diffWith.commit(), current.commit());

                Utils.print1or2ln(
                        "Committed on %s%s%s%s since %s [%s]".formatted(
                                GREEN, currentBranch, CLEAR, stats, date(ancestor), diff),
                        " -- %s%s%s".formatted(DIM, ancestor.getShortMessage(), CLEAR),
                        width);

                var diffEntries = gt.diff(ancestor, current.commit());
                if (!diffEntries.isEmpty()) {
                    System.out.println();
                    for (DiffEntry entry : diffEntries) {
                        switch (entry.getChangeType()) {
                            case RENAME:
                                System.out.printf(" R %s%s%s <- %s%s%s%n",
                                                  YELLOW_DIM, entry.getNewPath(), CLEAR,
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
                                                  YELLOW_DIM, path(entry.getNewPath()), CLEAR,
                                                  DIM, path(entry.getOldPath()), CLEAR);
                                break;
                        }
                    }
                }
            } else {
                Utils.print1or2ln(
                        "No commits on %s%s%s since %s [%s]".formatted(
                                GREEN, currentBranch, CLEAR, date(diffWith.commit()), diff),
                        " -- %s%s%s".formatted(DIM, diffWith.commit().getShortMessage(), CLEAR),
                        width);
            }

            // Check for staged and unstaged changes.
            List<DiffEntry> staged = git
                    .diff()
                    .setShowNameAndStatusOnly(true)
                    .setCached(true)
                    .call();
            List<DiffEntry> unstaged = git
                    .diff()
                    .setShowNameAndStatusOnly(true)
                    .setCached(false)
                    .call()
                    .stream()
                    .filter(i -> {
                        if (i.getChangeType() == DiffEntry.ChangeType.DELETE) {
                            try {
                                var path = gt.getRepositoryRoot().resolve(i.getOldPath());
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
                    })
                    .toList();

            if (!staged.isEmpty() || !unstaged.isEmpty()) {
                System.out.println();
                System.out.printf("%sUncommitted%s changes on %s%s%s:%n",
                                  RED,
                                  CLEAR,
                                  YELLOW_BOLD,
                                  currentBranch,
                                  CLEAR);
                System.out.println();

                Map<String, FileStatus> st = unstaged
                        .stream()
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
        } catch (GitAPIException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
