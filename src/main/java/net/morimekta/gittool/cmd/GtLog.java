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

import static net.morimekta.gittool.util.Utils.date;
import static net.morimekta.strings.chr.Color.*;
import static net.morimekta.terminal.args.Flag.flag;
import static net.morimekta.terminal.args.Option.option;

/**
 * Interactively manage branches.
 */
public class GtLog extends Command {
    private String branch = null;
    private boolean left = false;
    private boolean right = false;
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
                    if (leftLog.isEmpty()) {
                        System.out.printf("No commits on %s%s%s since %s -- %s%s%s%n",
                                GREEN,
                                currentBranch,
                                CLEAR,
                                date(ancestor),
                                DIM,
                                ancestor.getShortMessage(),
                                CLEAR);
                    } else {
                        System.out.printf("Commits on %s%s%s since %s -- %s%s%s%n",
                                GREEN,
                                currentBranch,
                                CLEAR,
                                date(ancestor),
                                DIM,
                                ancestor.getShortMessage(),
                                CLEAR);
                        System.out.println();
                        leftLog.forEach(co -> {
                            System.out.printf(
                                    "+ %s%s%s %s %s%s%s%n",
                                    GREEN,
                                    co.getName().substring(0, 7),
                                    CLEAR,
                                    date(co),
                                    DIM,
                                    co.getShortMessage(),
                                    CLEAR);
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
                        System.out.printf("No commits on %s%s%s since %s -- %s%s%s%n",
                                RED,
                                diffWithBranch,
                                CLEAR,
                                date(ancestor),
                                DIM,
                                ancestor.getShortMessage(),
                                CLEAR);
                    } else {
                        System.out.printf("Commits on %s%s%s since %s -- %s%s%s%n",
                                RED,
                                diffWithBranch,
                                CLEAR,
                                date(ancestor),
                                DIM,
                                ancestor.getShortMessage(),
                                CLEAR);
                        System.out.println();
                        rightLog.forEach(co -> {
                            System.out.printf(
                                    "- %s%s%s %s %s%s%s%n",
                                    RED,
                                    co.getName().substring(0, 7),
                                    CLEAR,
                                    date(co),
                                    DIM,
                                    co.getShortMessage(),
                                    CLEAR);
                        });
                    }
                }
            } else {
                System.out.printf("No commits on %s%s%s since %s -- %s%s%s%n",
                        BLUE,
                        currentBranch,
                        CLEAR,
                        date(current.commit()),
                        DIM,
                        current.commit().getShortMessage(),
                        CLEAR);
            }
        } catch (GitAPIException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
