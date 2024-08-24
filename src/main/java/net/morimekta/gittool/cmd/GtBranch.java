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
import net.morimekta.io.tty.TTYMode;
import net.morimekta.strings.chr.Char;
import net.morimekta.strings.chr.Color;
import net.morimekta.terminal.LinePrinter;
import net.morimekta.terminal.Terminal;
import net.morimekta.terminal.args.ArgParser;
import net.morimekta.terminal.selection.Selection;
import net.morimekta.terminal.selection.SelectionReaction;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.morimekta.collect.UnmodifiableList.asList;
import static net.morimekta.strings.StringUtil.printableWidth;
import static net.morimekta.strings.StringUtil.rightPad;
import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;
import static net.morimekta.strings.chr.Color.YELLOW;

/**
 * Interactively manage branches.
 */
public class GtBranch extends Command {
    private int longestBranchName = 0;
    private int longestRemoteName = 0;

    private enum BranchAction {
        CHECKOUT,
        DELETE,
        RENAME,
        SET_DIFFBASE,
    }

    private class BranchInfo {
        Ref ref;

        boolean current     = false;
        boolean uncommitted = false;

        String name     = null;
        String diffbase = null;
        String remote   = null;

        int commits = 0;
        int missing = 0;

        private void clr(StringBuilder builder, Color baseColor) {
            builder.append(CLEAR);
            if (baseColor != null) {
                builder.append(baseColor);
            }
        }

        String branchLine(Color baseColor) {
            StringBuilder builder = new StringBuilder();
            if (baseColor != null) {
                builder.append(baseColor);
            }
            if (current) {
                builder.append("* ").append(GREEN);
            } else {
                builder.append("  ").append(YELLOW);
            }
            builder.append(rightPad(name, longestBranchName));
            clr(builder, baseColor);
            if (diffbase.equals(name)) {
                builder.append("    ")
                       .append(" ".repeat(longestRemoteName));
            } else if (remote != null) {
                builder.append(" <- ")
                       .append(DIM)
                       .append(rightPad(remote, longestRemoteName));
                clr(builder, baseColor);
            } else {
                builder.append(" d: ")
                       .append(new Color(YELLOW, DIM))
                       .append(rightPad(diffbase, longestRemoteName));
                clr(builder, baseColor);
            }

            if (commits > 0 || missing > 0) {
                builder.append(" [");
                if (commits == 0) {
                    builder.append(CLR_SUBS).append("-").append(missing);
                    clr(builder, baseColor);
                } else if (missing == 0) {
                    builder.append(CLR_ADDS).append("+").append(commits);
                    clr(builder, baseColor);
                } else {
                    builder.append(CLR_ADDS).append("+").append(commits);
                    clr(builder, baseColor);
                    builder.append(",")
                           .append(CLR_SUBS).append("-").append(missing);
                    clr(builder, baseColor);
                }

                if (remote != null && !diffbase.equals(remote)) {
                    builder.append(",")
                           .append(BOLD)
                           .append("%%");
                    clr(builder, baseColor);
                }

                builder.append("]");
            }

            if (uncommitted) {
                builder.append(" -- ")
                       .append(new Color(BOLD, RED))
                       .append("MOD");
                clr(builder, baseColor);
                builder.append(" --");
            }

            return builder.toString();
        }

        String selectionLine(Color baseColor) {
            StringBuilder builder = new StringBuilder();

            builder.append(DIM);
            builder.append(name);
            builder.append(CLEAR);
            if (baseColor != null) {
                builder.append(baseColor);
            }
            return builder.toString();
        }
    }

    private List<BranchInfo> branches = new LinkedList<>();

    public GtBranch(ArgParser.Builder builder) {
    }

    private String branchName(Ref ref) {
        if (ref.getName().startsWith("refs/heads/")) {
            return ref.getName().substring(11);
        }
        return ref.getName();
    }

    private boolean hasUncommitted() throws GitAPIException {
        return !git.diff()
                   .setShowNameAndStatusOnly(true)
                   .setCached(true)
                   .call().isEmpty() ||
               !git.diff()
                   .setShowNameAndStatusOnly(true)
                   .setCached(false)
                   .call().isEmpty();
    }

    private SelectionReaction onSelect(BranchInfo info, LinePrinter printer) {
        if (info.current) {
            printer.println("Already on branch " + new Color(YELLOW, DIM) + info.name + CLEAR + "...");
            return SelectionReaction.EXIT;
        } else if (currentInfo.uncommitted) {
            printer.warn("Current branch has uncommitted changes");
            return SelectionReaction.STAY;
        } else {
            action = BranchAction.CHECKOUT;
            return SelectionReaction.SELECT;
        }
    }

    private SelectionReaction onDelete(int idx, BranchInfo info, LinePrinter printer) {
        if (info.current) {
            printer.warn("Unable to delete current branch");
            return SelectionReaction.STAY;
        }
        action = BranchAction.DELETE;
        return SelectionReaction.SELECT;
    }

    private SelectionReaction onSetDiffbase(int idx, BranchInfo ignore1, LinePrinter ignore2) {
        action = BranchAction.SET_DIFFBASE;
        return SelectionReaction.SELECT;
    }

    private SelectionReaction onRename(int idx, BranchInfo branch, LinePrinter printer) {
        if (branch.name.equals(gt.getDefaultBranch())) {
            printer.warn("Not allowed to rename default branch");
            return SelectionReaction.STAY;
        }
        action = BranchAction.RENAME;
        return SelectionReaction.SELECT;
    }

    private BranchInfo   currentInfo = null;
    private Repository   repository  = null;
    private BranchAction action      = null;
    private Git          git         = null;
    private GitTool      gt          = null;
    private String       prompt      = null;

    private BranchInfo refreshBranchList(String selected) throws IOException, GitAPIException {
        branches.clear();

        ListBranchCommand bl = git.branchList();
        List<Ref> refs = bl.call();

        String current = repository.getFullBranch();
        BranchInfo selectedInfo = null;

        prompt = "Manage branches from <untracked>:";
        for (Ref ref : refs) {
            BranchInfo info = new BranchInfo();
            info.ref = ref;

            info.name = branchName(ref);
            if (ref.getName().equals(current)) {
                info.current = true;
                info.uncommitted = hasUncommitted();
                currentInfo = info;
            }
            if (selected == null) {
                if (ref.getName().equals(current)) {
                    selectedInfo = info;
                }
            } else {
                if (info.name.equals(selected)) {
                    selectedInfo = info;
                }
            }

            info.diffbase = gt.getDiffbase(info.name);
            info.remote = gt.getRemote(info.name);

            Ref currentRef = repository.getRefDatabase().findRef(gt.refName(info.name));
            Ref diffWithRef = repository.getRefDatabase().findRef(gt.refName(info.diffbase));

            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffWithRef.getObjectId();

            List<RevCommit> localCommits = asList(git.log().addRange(diffWithHead, currentHead).call());
            List<RevCommit> remoteCommits = asList(git.log().addRange(currentHead, diffWithHead).call());

            info.commits = localCommits.size();
            info.missing = remoteCommits.size();

            longestBranchName = Math.max(longestBranchName, printableWidth(info.name));
            longestRemoteName = Math.max(longestRemoteName, printableWidth(info.diffbase));

            branches.add(info);
        }

        Comparator<BranchInfo> comparator = (l, r) -> {
            if (l.name.equals(gt.getDefaultBranch())) {
                return -1;
            }
            if (r.name.equals(gt.getDefaultBranch())) {
                return 1;
            }
            return l.name.compareTo(r.name);
        };
        branches.sort(comparator);
        return selectedInfo == null ? currentInfo : selectedInfo;
    }

    @Override
    public void execute(GitTool gt) throws IOException {
        this.gt = gt;

        try (Terminal terminal = new Terminal(TTYMode.COOKED)) {
            try (Repository repositoryResource = gt.getRepository()) {
                this.repository = repositoryResource;
                this.git = new Git(repositoryResource);
                BranchInfo tmpSelected = refreshBranchList(null);
                prompt = "Manage branches from '" + currentInfo.name + "':";

                while (true) {
                    action = null;
                    try (Selection<BranchInfo> selection = Selection
                            .newBuilder(branches)
                            .on(Char.CR, "select", (i, b, sel) -> {
                                action = BranchAction.CHECKOUT;
                                return SelectionReaction.SELECT;
                            })
                            .on('q', "quit", SelectionReaction.EXIT)
                            .on('d', "set diff-base", (i, b, sel) -> {
                                if (b.name.equals(gt.getDefaultBranch())) {
                                    sel.warn("Not allowed to set diff-base on default branch.");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.SET_DIFFBASE;
                                return SelectionReaction.SELECT;
                            })
                            .on('m', "move", (i, b, sel) -> {
                                if (b.name.equals(gt.getDefaultBranch())) {
                                    sel.warn("Not allowed to rename default branch.");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.RENAME;
                                return SelectionReaction.SELECT;
                            })
                            .on('D', "delete", (i, b, sel) -> {
                                if (b.name.equals(gt.getDefaultBranch())) {
                                    sel.warn("Not allowed to delete default branch.");
                                    return SelectionReaction.STAY;
                                }
                                if (b.current) {
                                    sel.warn("Unable to delete current branch");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.DELETE;
                                return SelectionReaction.SELECT;
                            })
                            .printer(BranchInfo::branchLine)
                            .initial(tmpSelected)
                            .build()) {
                        tmpSelected = selection.runSelection();
                    } catch (UncheckedIOException e) {
                        // Most likely: User interrupted:
                        // <ESC>, <CTRL-C> etc.
                        System.out.println(e.getMessage());
                        return;
                    }
                    if (tmpSelected == null) {
                        // command action EXIT.
                        return;
                    }
                    if (action == null) {
                        // Should be impossible?
                        continue;
                    }
                    final BranchInfo selected = tmpSelected;

                    switch (action) {
                        case CHECKOUT: {
                            Ref ref = git.checkout()
                                         .setName(selected.name)
                                         .call();
                            if (ref == null) {
                                terminal.lp().error("No ref from checkout op...");
                                break;
                            }
                            return;
                        }
                        case DELETE: {
                            if (selected.commits == 0 || terminal.confirm(
                                    "Do you really want to delete branch " +
                                    YELLOW + selected.name + CLEAR + " with " +
                                    GREEN + "+" + selected.commits + CLEAR + " commits?")) {
                                try {
                                    git.branchDelete()
                                       .setBranchNames(selected.name)
                                       .call();
                                } catch (GitAPIException se) {
                                    terminal.lp().println(se.getMessage());
                                    if (terminal.confirm("Do you "
                                                         + BOLD
                                                         + "really"
                                                         + CLEAR
                                                         + " want to delete branch?")) {
                                        git.branchDelete()
                                           .setBranchNames(selected.name)
                                           .setForce(true)
                                           .call();
                                    } else {
                                        terminal.lp().info("Delete canceled.");
                                        return;
                                    }
                                }
                                terminal.lp().info("Deleted branch " + RED + selected.name + CLEAR + "!");
                                tmpSelected = currentInfo;
                            } else {
                                terminal.lp().info("Delete canceled.");
                                return;
                            }
                            tmpSelected = refreshBranchList(tmpSelected.name);
                            terminal.lp().println("");
                            break;
                        }
                        case RENAME: {
                            String name;
                            try {
                                name = terminal.readLine("New name for " + YELLOW + selected.name + CLEAR);
                            } catch (UncheckedIOException e) {
                                // Most likely user interruption.
                                terminal.lp().info(e.getMessage());
                                terminal.lp().println("");
                                break;
                            }
                            if (selected.name.equals(name)) {
                                terminal.lp().info("Same same same...");
                                terminal.lp().println("");
                                break;
                            }

                            Ref ref = git.branchRename()
                                         .setOldName(selected.name)
                                         .setNewName(name)
                                         .call();
                            if (ref == null) {
                                terminal.lp().error("No ref from branch rename operation...");
                                return;
                            }
                            terminal.lp().println("");
                            tmpSelected = refreshBranchList(selected.name);
                            break;
                        }
                        case SET_DIFFBASE: {
                            if (selected.name.equals(gt.getDefaultBranch())) {
                                // TODO: Replace list with remotes only...
                                terminal.lp()
                                        .warn(format("Setting diffbase on %s%s%s branch!", BOLD, selected.name, CLEAR));
                                terminal.lp().println("");
                                break;
                            }

                            List<BranchInfo> options = new LinkedList<>(branches)
                                    .stream()
                                    .filter(b -> {
                                        // cannot have self as diffbase
                                        if (b == selected) return false;
                                        // avoid circular diffs.
                                        return !selected.name.equals(b.diffbase);
                                    })
                                    .collect(Collectors.toList());
                            if (options.isEmpty()) {
                                terminal.lp().info("No possible diffbase branches for " + selected.name);
                                break;
                            }
                            terminal.lp().println("");

                            BranchInfo newDiffBase;
                            try (var selection = Selection
                                    .newBuilder(options)
                                    .prompt("Select diff-base for '" + selected.name + "':")
                                    .on(Char.CR, "select", SelectionReaction.SELECT)
                                    .on('q', "quit", SelectionReaction.EXIT)
                                    .on('c', "clear", (ignore) -> {
                                        try {
                                            StoredConfig config = git.getRepository().getConfig();
                                            config.unset("branch", selected.name, "diffbase");
                                            config.save();
                                            return SelectionReaction.EXIT;
                                        } catch (IOException e) {
                                            throw new RuntimeException(e.getMessage(), e);
                                        }
                                    })
                                    .build()) {
                                newDiffBase = selection.runSelection();
                            }
                            if (newDiffBase == null) {
                                // None selected.
                                continue;
                            }

                            BranchInfo oldDiffbase = branches.stream()
                                                             .filter(b -> b.name.equals(selected.diffbase))
                                                             .findFirst()
                                                             .orElse(null);
                            if (oldDiffbase == newDiffBase) {
                                terminal.lp().println("Same diff-base as before: " + newDiffBase.name);
                                terminal.lp().println("");
                                continue;
                            }
                            StoredConfig config = git.getRepository().getConfig();
                            config.setString("branch", selected.name, "diffbase", newDiffBase.name);
                            config.save();
                            tmpSelected = refreshBranchList(selected.name);
                            terminal.lp().println("");
                            break;
                        }
                        default: {
                            terminal.lp().fatal("Not implemented: " + action.name());
                            return;
                        }
                    }
                }
            } catch (GitAPIException e) {
                terminal.lp().fatal("GIT: " + e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }
}
