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
import net.morimekta.terminal.args.ArgParser;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

import static java.lang.String.format;
import static net.morimekta.gittool.util.Colors.YELLOW_DIM;
import static net.morimekta.gittool.util.Utils.date;
import static net.morimekta.gittool.util.Utils.print1or2ln;
import static net.morimekta.strings.StringUtil.clipWidth;
import static net.morimekta.strings.chr.Color.BLUE;
import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;
import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Option.option;

/**
 * Interactively manage branches.
 */
public class GtLog extends Command {
    private String  branch = null;
    private boolean left   = false;
    private boolean right  = false;
    private boolean remote = false;

    public GtLog(ArgParser.Builder builder) {
        builder.add(option("--branch", "b", "Show status for branch, if not set diff to current", str -> branch = str));
        builder.add(flag("--left", "l", "Show left side", b -> left = b).defaultOn());
        builder.add(flag("--right", "r", "Show right side", b -> right = b).defaultOff());
        builder.add(flag("--remote", "R", "Show diff to remote", b -> remote = b).defaultOff());
    }

    @Override
    public void execute(GitTool gt) throws IOException {
        try {
            var width = gt.terminalWidth();
            if (!left && !right) {
                left = true;
            }

            Repository repository = gt.getRepository();

            var currentBranch = branch != null ? branch : repository.getBranch();
            var currentRef = repository.getRefDatabase().findRef(gt.refName(currentBranch));
            var current = new BranchInfo(currentRef, gt);

            if (remote && current.remote().isEmpty()) {
                System.err.println("No remote for " + currentBranch);
                return;
            }

            String diffWithBranch = remote ? current.remote() : current.diffBase();
            Ref diffWithRef = repository.getRefDatabase().findRef(gt.refName(diffWithBranch));
            if (diffWithRef == null) {
                System.out.printf("No such branch %s%s%s%n", BOLD, diffWithBranch, CLEAR);
                return;
            }

            var diffWith = new BranchInfo(diffWithRef, gt);

            if (!current.commit().equals(diffWith.commit())) {
                var log = gt.log(diffWith.commit(), current.commit());

                if (left) {
                    var leftLog = log.local();
                    var ancestor = gt.lastCommonAncestor(diffWith.commit(), current.commit());

                    String diff = gt.isRemote(diffWithBranch)
                                  ? format("->%s%s%s", BLUE, diffWithBranch, CLEAR)
                                  : format("d:%s%s%s", YELLOW_DIM, diffWithBranch, CLEAR);
                    if (leftLog.isEmpty()) {
                        print1or2ln(
                                "No commits on on %s%s%s since %s [%s]".formatted(
                                        GREEN, currentBranch, CLEAR, date(ancestor), diff),
                                " -- %s%s%s".formatted(DIM, ancestor.getShortMessage(), CLEAR),
                                width);
                    } else {
                        print1or2ln(
                                "Commits on on %s%s%s since %s [%s]".formatted(
                                        GREEN, currentBranch, CLEAR, date(ancestor), diff),
                                " -- %s%s%s".formatted(DIM, ancestor.getShortMessage(), CLEAR),
                                width);
                        System.out.println();
                        leftLog.forEach(co -> {
                            System.out.println(clipWidth(
                                    "+ %s%s%s %s %s%s%s".formatted(
                                            GREEN,
                                            co.abbreviate(7).name(),
                                            CLEAR,
                                            date(co),
                                            DIM,
                                            co.getShortMessage(),
                                            CLEAR),
                                    width));
                        });
                    }
                }

                if (right) {
                    if (left) {
                        System.out.println();
                    }

                    var rightLog = log.remote();
                    var ancestor = gt.lastCommonAncestor(current.commit(), diffWith.commit());
                    if (rightLog.isEmpty()) {
                        print1or2ln(
                                "No commits on %s%s%s since %s [d:%s%s%s]".formatted(
                                        RED, diffWithBranch, CLEAR,
                                        date(ancestor),
                                        YELLOW_DIM, currentBranch, CLEAR),
                                left ? "" : " -- %s%s%s".formatted(DIM, ancestor.getShortMessage(), CLEAR),
                                width);
                    } else {
                        print1or2ln(
                                "Commits on on %s%s%s since %s [d:%s%s%s]".formatted(
                                        RED, diffWithBranch, CLEAR,
                                        date(ancestor),
                                        YELLOW_DIM, currentBranch, CLEAR),
                                left ? "" : " -- %s%s%s".formatted(DIM, ancestor.getShortMessage(), CLEAR),
                                width);
                        System.out.println();
                        rightLog.forEach(co -> {
                            System.out.println(clipWidth(
                                    "- %s%s%s %s %s%s%s".formatted(
                                            RED,
                                            co.abbreviate(7).name(),
                                            CLEAR,
                                            date(co),
                                            DIM,
                                            co.getShortMessage(),
                                            CLEAR),
                                    width));
                        });
                    }
                }
            } else {
                print1or2ln(
                        "No commits on %s%s%s since %s".formatted(
                                BLUE, currentBranch, CLEAR, date(current.commit())),
                        " -- %s%s%s".formatted(
                                DIM, current.commit().getShortMessage(), CLEAR),
                        width);
            }
        } catch (GitAPIException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
