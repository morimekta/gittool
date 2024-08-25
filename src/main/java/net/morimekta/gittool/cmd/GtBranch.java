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
import net.morimekta.gittool.util.BranchInfo;
import net.morimekta.io.tty.TTYMode;
import net.morimekta.strings.chr.Char;
import net.morimekta.strings.chr.Color;
import net.morimekta.terminal.Terminal;
import net.morimekta.terminal.args.ArgParser;
import net.morimekta.terminal.selection.Selection;
import net.morimekta.terminal.selection.SelectionReaction;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static net.morimekta.strings.StringUtil.printableWidth;
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

    private final List<BranchInfo> branches = new LinkedList<>();

    public GtBranch(ArgParser.Builder ignore) {
    }

    private String branchName(Ref ref) {
        if (ref.getName().startsWith("refs/heads/")) {
            return ref.getName().substring(11);
        }
        return ref.getName();
    }

    private BranchInfo   currentInfo = null;
    private BranchAction action      = null;
    private GitTool      gt          = null;
    private String       prompt      = null;

    private BranchInfo refreshBranchList(String selected) throws IOException, GitAPIException {
        branches.clear();

        ListBranchCommand bl = gt.getGit().branchList();
        List<Ref> refs = bl.call();

        prompt = "Manage branches from <untracked>:";
        BranchInfo selectedInfo = null;
        for (Ref ref : refs) {
            BranchInfo info = new BranchInfo(ref, gt);
            if (info.isCurrent()) {
                currentInfo = info;
            }
            if (info.name().equals(selected)) {
                selectedInfo = info;
            }

            longestBranchName = Math.max(longestBranchName, printableWidth(info.name()));
            longestRemoteName = Math.max(longestRemoteName, printableWidth(info.diffBase()));

            branches.add(info);
        }

        branches.sort(Comparator.naturalOrder());
        return selectedInfo == null ? currentInfo : selectedInfo;
    }

    @Override
    public void execute(GitTool gt) throws IOException {
        this.gt = gt;

        try (Terminal terminal = new Terminal(gt.tty, TTYMode.COOKED)) {
            try {
                BranchInfo tmpSelected = refreshBranchList(null);
                prompt = "Manage branches from '" + tmpSelected.name() + "':";

                while (true) {
                    action = null;
                    try (Selection<BranchInfo> selection = Selection
                            .newBuilder(branches)
                            .prompt(prompt)
                            .tty(terminal.tty())
                            .on(Char.CR, "select", (i, b, sel) -> {
                                action = BranchAction.CHECKOUT;
                                return SelectionReaction.SELECT;
                            })
                            .on('q', "quit", SelectionReaction.EXIT)
                            .on('d', "set diffbase", (i, b, sel) -> {
                                if (b.isDefault()) {
                                    sel.warn("Not allowed to set diffbase on default branch.");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.SET_DIFFBASE;
                                return SelectionReaction.SELECT;
                            })
                            .on('m', "move", (i, b, sel) -> {
                                if (b.isDefault()) {
                                    sel.warn("Not allowed to rename default branch.");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.RENAME;
                                return SelectionReaction.SELECT;
                            })
                            .on('D', "delete", (i, b, sel) -> {
                                if (b.isDefault()) {
                                    sel.warn("Not allowed to delete default branch.");
                                    return SelectionReaction.STAY;
                                }
                                if (b.isCurrent()) {
                                    sel.warn("Unable to delete current branch");
                                    return SelectionReaction.STAY;
                                }
                                action = BranchAction.DELETE;
                                return SelectionReaction.SELECT;
                            })
                            .printer((b, bg) -> b.branchLine(bg, longestBranchName, longestRemoteName))
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
                            if (selected.isCurrent()) {
                                terminal.lp()
                                        .println("Already on branch "
                                                 + new Color(YELLOW, DIM)
                                                 + currentInfo.name()
                                                 + CLEAR);
                                return;
                            } else if (currentInfo.hasUncommitted()) {
                                terminal.lp()
                                        .warn("Current branch "
                                              + new Color(YELLOW, DIM)
                                              + currentInfo.name()
                                              + CLEAR
                                              + " has uncommitted changes.");
                                return;
                            }
                            Ref ref = gt.getGit()
                                        .checkout()
                                        .setName(selected.name())
                                        .call();
                            if (ref == null) {
                                terminal.lp().error("No ref from checkout op...");
                                break;
                            }
                            return;
                        }
                        case DELETE: {
                            if (selected.localCommits() == 0 || terminal.confirm(
                                    "Do you " + BOLD + "really" + CLEAR + " want to delete branch " +
                                    YELLOW + selected.name() + CLEAR + " with " +
                                    GREEN + "+" + selected.localCommits() + CLEAR + " commits?")) {
                                gt.getGit().branchDelete()
                                  .setBranchNames(selected.name())
                                  .setForce(true)
                                  .call();
                                terminal.lp().info("Deleted branch " + RED + selected.name() + CLEAR + "!");
                                tmpSelected = currentInfo;
                            } else {
                                terminal.lp().info("Delete canceled.");
                                return;
                            }
                            tmpSelected = refreshBranchList(tmpSelected.name());
                            terminal.lp().println("");
                            break;
                        }
                        case RENAME: {
                            String name;
                            try {
                                name = terminal.readLine("New name for " + YELLOW + selected.name() + CLEAR);
                            } catch (UncheckedIOException e) {
                                // Most likely user interruption.
                                terminal.lp().info(e.getMessage());
                                terminal.lp().println("");
                                break;
                            }
                            if (selected.name().equals(name)) {
                                terminal.lp().info("Same same same...");
                                terminal.lp().println("");
                                break;
                            }

                            Ref ref = gt.getGit()
                                        .branchRename()
                                        .setOldName(selected.name())
                                        .setNewName(name)
                                        .call();
                            if (ref == null) {
                                terminal.lp().error("No ref from branch rename operation...");
                                return;
                            }
                            terminal.lp().println("");
                            tmpSelected = refreshBranchList(name);
                            break;
                        }
                        case SET_DIFFBASE: {
                            List<BranchInfo> options = new LinkedList<>(branches)
                                    .stream()
                                    .filter(b -> {
                                        // cannot have self as diffbase
                                        if (b == selected) return false;
                                        // avoid circular diffs.
                                        return !selected.name().equals(b.diffBase());
                                    })
                                    .collect(Collectors.toList());
                            if (options.isEmpty()) {
                                terminal.lp().info("No possible diffbase branches for " + selected.name());
                                break;
                            }
                            terminal.lp().println("");

                            BranchInfo newDiffBase;
                            try (var selection = Selection
                                    .newBuilder(options)
                                    .tty(terminal.tty())
                                    .prompt("Select diffbase for '" + selected.name() + "':")
                                    .on(Char.CR, "select", SelectionReaction.SELECT)
                                    .on('q', "quit", SelectionReaction.EXIT)
                                    .on('c', "clear", (ignore) -> {
                                        try {
                                            StoredConfig config = gt.getRepository().getConfig();
                                            config.unset("branch", selected.name(), "diffbase");
                                            config.save();
                                            return SelectionReaction.EXIT;
                                        } catch (IOException e) {
                                            throw new RuntimeException(e.getMessage(), e);
                                        }
                                    })
                                    .printer(BranchInfo::selectionLine)
                                    .build()) {
                                newDiffBase = selection.runSelection();
                            }
                            if (newDiffBase == null) {
                                tmpSelected = refreshBranchList(selected.name());
                                // None selected (quit or clear).
                                continue;
                            }

                            BranchInfo oldDiffbase = branches.stream()
                                                             .filter(b -> b.name().equals(selected.diffBase()))
                                                             .findFirst()
                                                             .orElse(null);
                            if (oldDiffbase == newDiffBase) {
                                terminal.lp().println("Same diffbase as before: " + newDiffBase.name());
                                terminal.lp().println("");
                                continue;
                            }
                            StoredConfig config = gt.getGit().getRepository().getConfig();
                            config.setString("branch", selected.name(), "diffbase", newDiffBase.name());
                            config.save();
                            tmpSelected = refreshBranchList(selected.name());
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
