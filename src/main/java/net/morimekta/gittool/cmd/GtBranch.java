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

import net.morimekta.console.args.ArgumentParser;
import net.morimekta.console.chr.Char;
import net.morimekta.console.chr.CharUtil;
import net.morimekta.console.chr.Color;
import net.morimekta.console.terminal.InputLine;
import net.morimekta.console.terminal.InputSelection;
import net.morimekta.console.terminal.LinePrinter;
import net.morimekta.console.terminal.Terminal;
import net.morimekta.console.util.STTY;
import net.morimekta.gittool.GitTool;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
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

        boolean current = false;
        boolean uncommitted = false;

        String name = null;
        String diffbase = null;
        String remote = null;

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
            builder.append(Strings.padEnd(name, longestBranchName, ' '));
            clr(builder, baseColor);
            if (diffbase.equals(name)) {
                builder.append("    ")
                       .append(Strings.padEnd("", longestRemoteName, ' '));
            } else if (remote != null) {
                builder.append(" <- ")
                       .append(DIM)
                       .append(Strings.padEnd(remote, longestRemoteName, ' '));
                clr(builder, baseColor);
            } else {
                builder.append(" d: ")
                       .append(new Color(YELLOW, DIM))
                       .append(Strings.padEnd(diffbase, longestRemoteName, ' '));
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

            builder.append(Color.DIM);
            builder.append(name);
            builder.append(CLEAR);
            if (baseColor != null) {
                builder.append(baseColor);
            }
            return builder.toString();
        }
    }

    private List<BranchInfo> branches = new LinkedList<>();

    public GtBranch(ArgumentParser parent) {
        super(parent);
    }

    @Override
    public ArgumentParser makeParser() {
        return new ArgumentParser(getParent().getProgram() + " branch", getParent().getVersion(), "Manage branches interactively.");
    }

    private String branchName(Ref ref) {
        if (ref.getName().startsWith("refs/heads/")) {
            return ref.getName().substring(11);
        }
        return ref.getName();
    }

    private boolean hasUncommitted() throws GitAPIException {
        return git.diff()
                  .setShowNameAndStatusOnly(true)
                  .setCached(true)
                  .call()
                  .size() > 0 ||
               git.diff()
                  .setShowNameAndStatusOnly(true)
                  .setCached(false)
                  .call()
                  .size() > 0;
    }

    private InputSelection.Reaction onSelect(BranchInfo info, LinePrinter printer) {
        if (info.current) {
            printer.println("Already on branch " + new Color(YELLOW, DIM) + info.name + CLEAR + "...");
            return InputSelection.Reaction.EXIT;
        } else if (currentInfo.uncommitted) {
            printer.warn("Current branch has uncommitted changes");
            return InputSelection.Reaction.STAY;
        } else {
            action = BranchAction.CHECKOUT;
            return InputSelection.Reaction.SELECT;
        }
    }

    private InputSelection.Reaction onDelete(BranchInfo info, LinePrinter printer) {
        if (info.current) {
            printer.warn("Unable to delete current branch");
            return InputSelection.Reaction.STAY;
        }
        action = BranchAction.DELETE;
        return InputSelection.Reaction.SELECT;
    }

    private InputSelection.Reaction onSetDiffbase(BranchInfo ignore1, LinePrinter ignore2) {
        action = BranchAction.SET_DIFFBASE;
        return InputSelection.Reaction.SELECT;
    }

    private InputSelection.Reaction onRename(BranchInfo branch, LinePrinter printer) {
        if (branch.name.equals(gt.getDefaultBranch())) {
            printer.warn("Not allowed to rename default branch");
            return InputSelection.Reaction.STAY;
        }
        action = BranchAction.RENAME;
        return InputSelection.Reaction.SELECT;
    }

    private InputSelection.Reaction onExit(BranchInfo ignore1, LinePrinter ignore2) {
        return InputSelection.Reaction.EXIT;
    }

    private BranchInfo currentInfo = null;
    private Repository repository = null;
    private BranchAction action = null;
    private Git git = null;
    private GitTool gt = null;
    private String prompt = null;

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

            Ref currentRef = repository.getRef(gt.refName(info.name));
            Ref diffWithRef = repository.getRef(gt.refName(info.diffbase));

            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffWithRef.getObjectId();

            List<RevCommit> localCommits = ImmutableList.copyOf(git.log().addRange(diffWithHead, currentHead).call());
            List<RevCommit> remoteCommits = ImmutableList.copyOf(git.log().addRange(currentHead, diffWithHead).call());

            info.commits = localCommits.size();
            info.missing = remoteCommits.size();

            longestBranchName = Math.max(longestBranchName, CharUtil.printableWidth(info.name));
            longestRemoteName = Math.max(longestRemoteName, CharUtil.printableWidth(info.diffbase));

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

        ArrayList<InputSelection.Command<BranchInfo>> actions = new ArrayList<>(5);
        actions.add(new InputSelection.Command<>(Char.CR, "select", this::onSelect, true));
        actions.add(new InputSelection.Command<>('D', "delete", this::onDelete));
        actions.add(new InputSelection.Command<>('q', "quit", this::onExit));
        actions.add(new InputSelection.Command<>('d', "set diffbase", this::onSetDiffbase));
        actions.add(new InputSelection.Command<>('m', "move", this::onRename));

        STTY tty = new STTY();
        try (Terminal terminal = new Terminal(tty)) {
            try (Repository repositoryResource = gt.getRepository()) {
                this.repository = repositoryResource;
                this.git = new Git(repositoryResource);
                BranchInfo tmp = refreshBranchList(null);
                prompt = "Manage branches from '" + currentInfo.name + "':";

                while (true) {
                    action = null;
                    try {
                        InputSelection<BranchInfo> selection = new InputSelection<>(terminal,
                                                                                    prompt,
                                                                                    branches,
                                                                                    actions,
                                                                                    BranchInfo::branchLine);
                        tmp = selection.select(tmp);
                    } catch (UncheckedIOException e) {
                        // Most likely: User interrupted:
                        // <ESC>, <CTRL-C> etc.
                        System.out.println(e.getMessage());
                        return;
                    }
                    if (tmp == null) {
                        // command action EXIT.
                        return;
                    }
                    if (action == null) {
                        continue;
                    }
                    final BranchInfo selected = tmp;

                    switch (action) {
                        case CHECKOUT: {
                            Ref ref = git.checkout()
                                         .setName(selected.name)
                                         .call();
                            if (ref == null) {
                                terminal.error("No ref from checkout op...");
                                break;
                            }
                            return;
                        }
                        case DELETE: {
                            if (selected.name.equals(gt.getDefaultBranch())) {
                                terminal.info("Not allowed to delete default branch: " + selected.name);
                                break;
                            }

                            if (selected.commits == 0 || terminal.confirm(
                                    "Do you really want to delete branch " +
                                    YELLOW + selected.name + CLEAR + " with " +
                                    GREEN + "+" + selected.commits + CLEAR + " commits?")) {
                                try {
                                    git.branchDelete()
                                       .setBranchNames(selected.name)
                                       .call();
                                } catch (GitAPIException se) {
                                    terminal.println(se.getMessage());
                                    if (terminal.confirm("Do you " + BOLD + "really" + CLEAR + " want to delete branch?")) {
                                        git.branchDelete()
                                           .setBranchNames(selected.name)
                                           .setForce(true)
                                           .call();
                                    } else {
                                        terminal.info("Delete canceled.");
                                        return;
                                    }
                                }
                                terminal.info("Deleted branch " + RED + selected.name + CLEAR + "!");
                                tmp = currentInfo;
                            } else {
                                terminal.info("Delete canceled.");
                                return;
                            }
                            tmp = refreshBranchList(tmp.name);
                            terminal.println();
                            break;
                        }
                        case RENAME: {
                            String name;
                            try {
                                InputLine input = new InputLine(terminal,
                                                                "New name for " + YELLOW + selected.name + CLEAR);
                                name = input.readLine(selected.name);
                            } catch (UncheckedIOException e) {
                                // Most likely user interruption.
                                terminal.info(e.getMessage());
                                terminal.println();
                                break;
                            }
                            if (selected.name.equals(name)) {
                                terminal.info("Same same same...");
                                terminal.println();
                                break;
                            }

                            Ref ref = git.branchRename()
                                         .setOldName(selected.name)
                                         .setNewName(name)
                                         .call();
                            if (ref == null) {
                                terminal.error("No ref from branch rename operation...");
                                return;
                            }
                            terminal.println();
                            tmp = refreshBranchList(selected.name);
                            break;
                        }
                        case SET_DIFFBASE: {
                            if (selected.name.equals(gt.getDefaultBranch())) {
                                // TODO: Replace list with remotes only...
                                terminal.warn(format("Setting diffbase on %s%s%s branch!", Color.BOLD, selected.name, Color.CLEAR));
                                terminal.println();
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
                            if (options.size() == 0) {
                                terminal.info("No possible diffbase branches for " + selected.name);
                                break;
                            }
                            terminal.println();

                            ArrayList<InputSelection.Command<BranchInfo>> diffbaseActions = new ArrayList<>(5);
                            diffbaseActions.add(new InputSelection.Command<>(Char.CR, "select", (br, lp) -> InputSelection.Reaction.SELECT, true));
                            diffbaseActions.add(new InputSelection.Command<>('c', "clear", (br, lp) -> {
                                try {
                                    StoredConfig config = git.getRepository().getConfig();
                                    config.unset("branch", selected.name, "diffbase");
                                    config.save();
                                    return InputSelection.Reaction.EXIT;
                                } catch (IOException e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                            }));
                            diffbaseActions.add(new InputSelection.Command<>('q', "quit", (br, lp) -> InputSelection.Reaction.EXIT));

                            InputSelection<BranchInfo> selection = new InputSelection<>(terminal,
                                                                                        "Select diffbase for '" + selected.name + "':",
                                                                                        options,
                                                                                        diffbaseActions,
                                                                                        BranchInfo::selectionLine);
                            BranchInfo oldDiffbase = branches.stream().filter(b -> b.name.equals(selected.diffbase)).findFirst().orElse(null);
                            BranchInfo newDiffbase = selection.select(oldDiffbase);
                            if (newDiffbase != null) {
                                StoredConfig config = git.getRepository().getConfig();
                                config.setString("branch", selected.name, "diffbase", newDiffbase.name);
                                config.save();
                                tmp = refreshBranchList(selected.name);
                            }

                            terminal.println();
                            break;
                        }
                        default: {
                            terminal.warn("Not implemented: " + action.name());
                            terminal.println();
                            break;
                        }
                    }
                }
            } catch (GitAPIException e) {
                terminal.fatal("GIT: " + e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }
}
