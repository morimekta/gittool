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

import net.morimekta.file.TemporaryAssetFolder;
import net.morimekta.gittool.GitTool;
import net.morimekta.gittool.util.BranchInfo;
import net.morimekta.io.proc.SubProcess;
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static net.morimekta.gittool.util.Colors.YELLOW_DIM;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;
import static net.morimekta.strings.chr.Color.YELLOW;
import static net.morimekta.terminal.args.Option.option;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE;

/**
 * Interactively manage branches.
 */
public class GtDiff extends Command {
    private String branch = null;

    public GtDiff(ArgParser.Builder builder) {
        builder.add(option("--branch", "b", "Show diff against branch", str -> branch = str));
    }

    @Override
    public void execute(GitTool gt) throws IOException, GitAPIException {
        Repository repository = gt.getRepository();

        Ref currentRef = repository.getRefDatabase().findRef(gt.refName(repository.getBranch()));
        BranchInfo current = new BranchInfo(currentRef, gt);

        Ref diffWithRef;
        if (branch != null) {
            diffWithRef = repository.getRefDatabase().findRef(gt.refName(branch));
            if (diffWithRef == null) {
                System.out.println("No ref found for " + branch);
                return;
            }
        } else {
            diffWithRef = repository.getRefDatabase().findRef(gt.refName(current.diffBase()));
            if (diffWithRef == null) {
                System.out.println("No ref found for " + current.diffBase());
                return;
            }
        }
        BranchInfo diffWith = new BranchInfo(diffWithRef, gt);
        if (current.commit().equals(diffWith.commit()) && !current.hasUncommitted()) {
            return;
        }

        // Map from last known file path, to diff entry.
        Map<String, GtDiffEntry> diffEntryMap = new TreeMap<>();
        if (!current.commit().equals(diffWith.commit())) {
            var ancestor = gt.lastCommonAncestor(diffWith.commit(), current.commit());
            for (var entry : gt.diff(ancestor, current.commit())) {
                var gde = new GtDiffEntry();
                gde.fromGitPath = entry.getOldPath();
                gde.toGitPath = entry.getNewPath();
                gde.baseToHead = entry;
                if (entry.getChangeType() != DELETE) {
                    gde.key = gde.toGitPath;
                } else {
                    gde.key = gde.fromGitPath;
                }
                diffEntryMap.put(gde.key, gde);
            }
        }
        if (current.hasUncommitted()) {
            for (var entry : gt.getGit().diff()
                               .setShowNameAndStatusOnly(true)
                               .setCached(true)
                               .call()) {
                var gde = entry.getChangeType() == ADD
                          ? diffEntryMap.get(entry.getNewPath())
                          : diffEntryMap.get(entry.getOldPath());
                if (gde == null) {
                    gde = new GtDiffEntry();
                    gde.fromGitPath = entry.getOldPath();
                    gde.toGitPath = entry.getNewPath();
                    if (entry.getChangeType() == DELETE) {
                        gde.key = gde.fromGitPath;
                    } else {
                        gde.key = gde.toGitPath;
                    }
                    gde.staged = entry;
                    diffEntryMap.put(gde.key, gde);
                } else {
                    if (entry.getChangeType() != DELETE && !gde.key.equals(entry.getNewPath())) {
                        diffEntryMap.remove(gde.key);
                        gde.key = entry.getNewPath();
                        diffEntryMap.put(gde.key, gde);
                    }
                    gde.toGitPath = entry.getNewPath();
                    gde.staged = entry;
                }
            }
            for (var entry : gt.getGit().diff()
                               .setShowNameAndStatusOnly(true)
                               .setCached(false)
                               .call()) {
                var gde = entry.getChangeType() == ADD
                          ? diffEntryMap.get(entry.getNewPath())
                          : diffEntryMap.get(entry.getOldPath());
                if (gde == null) {
                    gde = new GtDiffEntry();
                    gde.fromGitPath = entry.getOldPath();
                    gde.toGitPath = entry.getNewPath();
                    if (entry.getChangeType() == DELETE) {
                        gde.key = gde.fromGitPath;
                    } else {
                        gde.key = gde.toGitPath;
                    }
                    gde.unstaged = entry;
                    diffEntryMap.put(gde.key, gde);
                } else {
                    if (entry.getChangeType() != DELETE && !gde.key.equals(entry.getNewPath())) {
                        diffEntryMap.remove(gde.key);
                        gde.key = entry.getNewPath();
                        diffEntryMap.put(entry.getNewPath(), gde);
                    }
                    gde.toGitPath = entry.getNewPath();
                    gde.unstaged = entry;
                }
            }
        }

        if (diffEntryMap.isEmpty()) {
            return;
        }

        Path tmp = Files.createTempDirectory("gt-diff");
        try (var taf = new TemporaryAssetFolder(tmp)) {
            var list = new ArrayList<Path>();
            for (var gde : diffEntryMap.values()) {
                if (gde.fromGitPath.equals("/dev/null")) {
                    if (gde.toGitPath.equals("/dev/null")) {
                        // created, then deleted.
                        System.out.printf("Skipping %s%s%s (new + delete)%n", DIM, gde.key, CLEAR);
                        continue;
                    }
                    System.out.printf("New    %s%s%s%n", GREEN, gde.toGitPath, CLEAR);
                    list.add(Path.of(gde.fromGitPath));
                    list.add(GitTool.pwd.relativize(gt.getRepositoryRoot().resolve(gde.toGitPath)));
                } else {
                    var oldObjectId = gde.baseToHead != null
                                      ? gde.baseToHead.getOldId() :
                                      (gde.staged != null
                                       ? gde.staged.getOldId()
                                       : gde.unstaged.getOldId());

                    var file = taf.resolvePath(gde.fromGitPath);
                    var dir = file.getParent();
                    if (!Files.exists(dir)) {
                        Files.createDirectories(dir);
                    }
                    try (var reader = repository.getObjectDatabase().newReader();
                         var out = new BufferedOutputStream(Files.newOutputStream(file))) {
                        reader.open(oldObjectId.toObjectId()).copyTo(out);
                    }
                    list.add(file);
                    if (gde.toGitPath.equals("/dev/null")) {
                        System.out.printf("Delete %s%s%s%n", RED, CLEAR, gde.key);
                        list.add(Path.of(gde.toGitPath));
                    } else {
                        if (gde.toGitPath.equals(gde.fromGitPath)) {
                            System.out.printf("Diff   %s%n", gde.toGitPath);
                        } else {
                            System.out.printf("Move   %s%s%s -> %s%s%s%n",
                                              YELLOW_DIM,
                                              gde.fromGitPath,
                                              CLEAR,
                                              YELLOW,
                                              gde.toGitPath,
                                              CLEAR);
                        }
                        list.add(GitTool.pwd.relativize(gt.getRepositoryRoot().resolve(gde.toGitPath)));
                    }
                }
            }

            var share = System.getenv("GT_SHARE");
            if (share == null) {
                share = "src/deb/share";
            }

            var args = new ArrayList<String>();
            args.add("gvim");
            args.add("+so " + share + "/diffall.vim");
            list.forEach(p -> args.add(p.toString()));

            var out = SubProcess
                    .newRunner(args.toArray(new String[0]))
                    .withDeadlineMs(TimeUnit.HOURS.toMillis(20))
                    .withDeadlineFlushMs(TimeUnit.HOURS.toMillis(20))
                    .run();
            if (out.getExitCode() != 0) {
                System.err.print(out.getError());
                System.out.print(out.getOutput());
            }
        }
    }

    private class GtDiffEntry {
        // If ultimately created, should be null.
        String fromGitPath;
        String toGitPath;
        String key;

        DiffEntry baseToHead;
        DiffEntry staged;
        DiffEntry unstaged;
    }
}
