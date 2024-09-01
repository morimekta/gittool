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

import net.morimekta.collect.UnmodifiableSortedSet;
import net.morimekta.gittool.GitTool;
import net.morimekta.gittool.util.BranchInfo;
import net.morimekta.gittool.util.SelectFile;
import net.morimekta.io.tty.TTYMode;
import net.morimekta.strings.chr.Char;
import net.morimekta.strings.chr.Color;
import net.morimekta.terminal.Terminal;
import net.morimekta.terminal.input.InputLine;
import net.morimekta.terminal.selection.Selection;
import net.morimekta.terminal.selection.SelectionReaction;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.morimekta.gittool.util.Colors.YELLOW_BOLD;
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

    private enum BranchAction {
        CHECKOUT,
        NEW,
        RENAME,
        SET_DIFFBASE,
        SET_DIFFBASE_REMOTE,
    }

    private final List<BranchInfo> branches = new LinkedList<>();

    public GtBranch() {}

    private BranchInfo   currentInfo = null;
    private BranchAction action      = null;
    private GitTool      gt          = null;
    private String       prompt      = null;

    private BranchInfo refreshBranchList(String selected) throws IOException, GitAPIException {
        branches.clear();

        List<Ref> refs = gt.getGit()
                           .branchList()
                           .call();

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

            // Initialize info.
            info.branchLine(null, longestBranchName);

            branches.add(info);
        }

        branches.sort(Comparator.naturalOrder());
        return selectedInfo == null ? currentInfo : selectedInfo;
    }

    private SelectionReaction onDelete(int idx, BranchInfo b, Selection<BranchInfo> sel) {
        if (b.isDefault()) {
            sel.warn("Not allowed to delete default branch.");
            return SelectionReaction.STAY;
        }

        try {
            if (b.localCommits() == 0 || sel.confirm(
                    "Do you " + BOLD + "really" + CLEAR + " want to delete branch " +
                    YELLOW + b.name() + CLEAR + " with " +
                    GREEN + "+" + b.localCommits() + CLEAR + " commits?")) {
                if (b.isCurrent()) {
                    sel.info("Checking out default branch %s%s%s!",
                             YELLOW_BOLD, gt.defaultBranch.get(), CLEAR);
                    // Check out default branch if we delete current.
                    gt.getGit().checkout()
                      .setName(gt.defaultBranch.get())
                      .call();
                }
                gt.getGit().branchDelete()
                  .setBranchNames(b.name())
                  .setForce(true)
                  .call();
                sel.info("Deleted branch %s%s%s!", RED, b.name(), CLEAR);
                branches.remove(b);
                return SelectionReaction.UPDATE_KEEP_POSITION;
            } else {
                sel.info("Delete canceled.");
                return SelectionReaction.STAY;
            }
        } catch (IOException | GitAPIException e) {
            sel.warn("Failed to delete branch %s%s%s: %s", RED, b.name(), CLEAR, e.getMessage());
            return SelectionReaction.EXIT;
        }
    }

    private SelectionReaction onSetDiffBase(int idx, BranchInfo b, Selection<BranchInfo> sel) {
        if (b.isDefault()) {
            sel.warn("Not allowed to set diffbase on default branch.");
            return SelectionReaction.STAY;
        }
        action = BranchAction.SET_DIFFBASE;
        return SelectionReaction.SELECT;
    }

    private SelectionReaction onSetDiffBaseRemote(int idx, BranchInfo b, Selection<BranchInfo> sel) {
        action = BranchAction.SET_DIFFBASE_REMOTE;
        return SelectionReaction.SELECT;
    }

    private void handleSetDiffBase(Terminal terminal, BranchInfo selected, boolean remotes) throws IOException, GitAPIException {
        List<BranchInfo> options;
        if (remotes) {
            options = new ArrayList<>();
            gt.getGit()
              .branchList()
              .setListMode(ListBranchCommand.ListMode.REMOTE)
              .call()
              .stream()
              .filter(r -> !r.getName().endsWith("/HEAD"))
              .forEach(ref -> options.add(new BranchInfo(ref, gt)));
        } else {
            options = new LinkedList<>(branches)
                    .stream()
                    .filter(b -> {
                        // cannot have self as diffbase
                        if (b == selected) return false;
                        // avoid circular diffs.
                        return !selected.name().equals(b.diffBase());
                    })
                    .collect(Collectors.toList());
        }
        if (options.isEmpty()) {
            terminal.lp().info("No possible diffbase branches for " + selected.name());
            terminal.lp().println("");
            return;
        }
        terminal.lp().println("");

        BranchInfo newDiffBase;
        try (var selection = Selection
                .newBuilder(options)
                .terminal(terminal)
                .prompt("Select diffbase for '" + selected.name() + "':")
                .on(Char.CR, "select", SelectionReaction.SELECT)
                .on('q', "quit", SelectionReaction.EXIT)
                .on('c', "clear", (ignore) -> {
                    try {
                        StoredConfig config = gt.getConfig();
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
            terminal.lp().println("");
            return;
        }

        BranchInfo oldDiffbase = branches.stream()
                                         .filter(b -> b.name().equals(selected.diffBase()))
                                         .findFirst()
                                         .orElse(null);
        if (oldDiffbase == newDiffBase) {
            terminal.lp().println("Same diffbase as before: " + newDiffBase.name());
            terminal.lp().println("");
            return;
        }
        StoredConfig config = gt.getConfig();
        config.setString("branch", selected.name(), "diffbase", newDiffBase.name());
        config.save();
        terminal.lp().println("");
    }

    private SelectionReaction onRename(int idx, BranchInfo b, Selection<BranchInfo> sel) {
        if (b.isDefault()) {
            sel.warn("Not allowed to rename default branch.");
            return SelectionReaction.STAY;
        }
        action = BranchAction.RENAME;
        return SelectionReaction.SELECT;
    }

    private BranchInfo handleRename(Terminal terminal, BranchInfo selected) throws IOException, GitAPIException {
        String name;
        try {
            name = inputNewBranchName(terminal, "New name for " + YELLOW + selected.name() + CLEAR);
        } catch (UncheckedIOException e) {
            // Most likely user interruption.
            terminal.lp().info(e.getMessage());
            terminal.lp().println("");
            return selected;
        }
        if (name == null) {
            return selected;
        }

        Ref ref = gt.getGit()
                    .branchRename()
                    .setOldName(selected.name())
                    .setNewName(name)
                    .call();
        if (ref == null) {
            terminal.lp().error("No ref from branch rename operation...");
            return null;
        }
        terminal.lp().println("");
        return refreshBranchList(name);
    }

    private SelectionReaction onNew(int idx, BranchInfo b, Selection<BranchInfo> sel) {
        if (gt.hasUncommitted.get()) {
            sel.warn("Uncommitted on current branch.");
            return SelectionReaction.STAY;
        }
        action = BranchAction.NEW;
        return SelectionReaction.SELECT;
    }

    private boolean handleNew(Terminal terminal, BranchInfo selected) throws IOException, GitAPIException {
        var files = new ArrayList<SelectFile>();
        if (!selected.isDefault() && selected.diffBaseCommit() != null) {
            var ancestor = gt.lastCommonAncestor(selected.diffBaseCommit(), selected.commit());

            for (var diff : gt.diff(ancestor, selected.commit())) {
                files.add(new SelectFile(diff));
            }
            if (!files.isEmpty()) {
                try (var selector = Selection
                        .newBuilder(files)
                        .terminal(terminal)
                        .prompt("Select files to include in new branch")
                        .on(' ', "toggle", (i, file, selection) -> {
                            file.selected = !file.selected;
                            return SelectionReaction.UPDATE_KEEP_ITEM;
                        })
                        .on('a', "all", (i, file, selection) -> {
                            for (var f : files) {
                                f.selected = true;
                            }
                            return SelectionReaction.UPDATE_KEEP_ITEM;
                        })
                        .on('n', "none", (i, file, selection) -> {
                            for (var f : files) {
                                f.selected = false;
                            }
                            return SelectionReaction.UPDATE_KEEP_ITEM;
                        })
                        .on(Char.CR, "continue", SelectionReaction.SELECT)
                        .on('q', "abort", SelectionReaction.EXIT)
                        .hiddenOn(Char.ESC, SelectionReaction.EXIT)
                        .printer(SelectFile::displayLine)
                        .build()) {
                    if (selector.runSelection() == null) {
                        return true;
                    }
                } catch (Exception e) {
                    terminal.lp().error(e.getMessage());
                    e.printStackTrace(terminal.printStream());
                    terminal.printStream().println();
                    return true;
                }
            }
        }

        String newName = inputNewBranchName(terminal, "New branch name");
        if (newName == null) {
            return true;
        }

        if (selected.isDefault()) {
            gt.getGit().checkout()
              .setName(selected.name())
              .call();
            gt.getGit().checkout()
              .setCreateBranch(true)
              .setName(newName)
              .call();
        } else {
            gt.getGit().checkout()
              .setName(selected.diffBase())
              .call();
            gt.getGit().checkout()
              .setCreateBranch(true)
              .setName(newName)
              .call();

            files.removeIf(f -> !f.selected);
            if (!files.isEmpty()) {
                var root = gt.getRepositoryRoot();
                try (var reader = gt.getRepository().getObjectDatabase().newReader()) {
                    for (var f : files) {
                        if (f.entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                            Files.delete(root.resolve(f.entry.getOldPath()));
                        } else {
                            var file = root.resolve(f.entry.getNewPath());
                            if (!Files.exists(file.getParent())) {
                                Files.createDirectories(file.getParent());
                            }
                            try (var out = Files.newOutputStream(
                                    root.resolve(f.entry.getNewPath()),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING)) {
                                reader.open(f.entry.getNewId().toObjectId()).copyTo(out);
                            }
                        }
                    }
                }
                gt.getGit().add().addFilepattern(".").call();
            }
        }
        return false;
    }

    private boolean handleCheckout(Terminal terminal, BranchInfo selected) throws IOException, GitAPIException {
        if (selected.isCurrent()) {
            terminal.lp()
                    .println("Already on branch "
                             + new Color(YELLOW, DIM)
                             + currentInfo.name()
                             + CLEAR);
            return false;
        } else if (currentInfo.hasUncommitted()) {
            terminal.lp()
                    .warn("Current branch "
                          + new Color(YELLOW, DIM)
                          + currentInfo.name()
                          + CLEAR
                          + " has uncommitted changes.");
            return false;
        }
        Ref ref = gt.getGit()
                    .checkout()
                    .setName(selected.name())
                    .call();
        if (ref == null) {
            terminal.lp().error("No ref from checkout op...");
            return true;
        }
        return false;
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
                            .terminal(terminal)
                            .prompt(prompt)
                            .on(Char.CR, "select", (i, b, sel) -> {
                                action = BranchAction.CHECKOUT;
                                return SelectionReaction.SELECT;
                            })
                            .on('b', "set diffbase", this::onSetDiffBase)
                            .on('B', "set remote diffbase", this::onSetDiffBaseRemote)
                            .on('D', "delete", this::onDelete)
                            .on('m', "move", this::onRename)
                            .on('n', "new", this::onNew)
                            .on('q', "quit", SelectionReaction.EXIT)
                            .printer((b, bg) -> b.branchLine(bg, longestBranchName))
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
                    switch (action) {
                        case CHECKOUT: {
                            if (handleCheckout(terminal, tmpSelected)) {
                                continue;
                            }
                            return;
                        }
                        case RENAME: {
                            tmpSelected = handleRename(terminal, tmpSelected);
                            if (tmpSelected != null) {
                                continue;
                            }
                            return;
                        }
                        case NEW: {
                            if (handleNew(terminal, tmpSelected)) {
                                continue;
                            }
                            return;
                        }
                        case SET_DIFFBASE: {
                            handleSetDiffBase(terminal, tmpSelected, false);
                            continue;
                        }
                        case SET_DIFFBASE_REMOTE: {
                            handleSetDiffBase(terminal, tmpSelected, true);
                            continue;
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

    private String inputNewBranchName(Terminal terminal, String message) {
        var charsPattern = Pattern.compile("[-_/a-zA-Z0-9]");
        var alphaPattern = Pattern.compile("[a-zA-Z]");
        var delimPattern = Pattern.compile("/");
        var completion = branches
                .stream()
                .map(BranchInfo::name)
                .flatMap(name -> {
                    var out = new ArrayList<String>();
                    var names = name.split("/");
                    var b = new StringBuilder();
                    for (String n : names) {
                        b.append(n).append('/');
                        out.add(b.toString());
                    }
                    return out.stream();
                })
                .collect(UnmodifiableSortedSet.toSortedSet());
        try (var lineReader = new InputLine(
                terminal,
                message,
                (c, lp) -> charsPattern.matcher("" + c).matches(),
                (l, lp) -> {
                    if (!alphaPattern.matcher(l).find()) {
                        lp.warn("Must contain at least 1 letter [a-z]");
                        return false;
                    } else if (l.startsWith("/") || l.startsWith("-") || l.startsWith("_")) {
                        lp.warn("Not allowed to start with '/', '-' or '_'");
                        return false;
                    } else if (l.endsWith("/")) {
                        lp.warn("Not allowed to end with '/'");
                        return false;
                    } else if (l.contains("//")) {
                        lp.warn("Not allowed to contain '//'");
                        return false;
                    } else if (l.contains("/")) {
                        var first = l.split("/")[0];
                        if (gt.remoteNames.get().contains(first)) {
                            lp.warn("Not allowed to start with remote name + '/'");
                            return false;
                        }
                    }
                    if (branches.stream().anyMatch(b -> b.name().equals(l))) {
                        lp.warn("Branch '%s' already exists", l);
                        return false;
                    }
                    return true;
                },
                (prefix, lp) -> {
                    if (prefix.isEmpty()) return null;
                    var next = completion.higher(prefix);
                    if (next != null && next.startsWith(prefix)) {
                        return next;
                    }
                    return null;
                },
                delimPattern
        )) {
            return lineReader.readLine();
        } catch (UncheckedIOException e) {
            return null;
        }
    }
}
