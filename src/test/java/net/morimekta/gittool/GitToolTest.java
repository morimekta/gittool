/*
 * Copyright (c) 2016, Stein Eldar Johnsen
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package net.morimekta.gittool;

import net.morimekta.testing.console.Console;
import net.morimekta.testing.junit5.ConsoleExtension;
import net.morimekta.testing.junit5.ConsoleSize;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.Files.writeString;
import static net.morimekta.file.FileUtil.readCanonicalPath;
import static net.morimekta.strings.StringUtil.stripNonPrintable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Disabled
@ExtendWith(ConsoleExtension.class)
@ConsoleSize(rows = 44, cols = 128)
public class GitToolTest {
    private Path                root;
    private Map<String, String> env;
    private Git                 git;

    @BeforeEach
    public void setUp(@TempDir Path tmp) throws IOException, GitAPIException {
        root = tmp;
        env = new HashMap<>();
        env.putAll(System.getenv());
        env.put("PWD", readCanonicalPath(root).toString());

        git = Git.init()
                 .setDirectory(root.toFile())
                 .setInitialBranch("master")
                 .call();
        var config = git.getRepository().getConfig();
        config.setBoolean("commit", null, "gpgsign", false);
        config.save();

        writeString(root.resolve("a.txt"), "a");

        git.add().addFilepattern(".").call();
        git.commit().setMessage("First commit").call();
    }

    @Test
    public void testGT_status1(Console console) {
        GitTool gt = new GitTool(console.tty(), env) {
            @Override
            protected void errorExit() {
            }
        };

        gt.execute("st");

        assertThat(cleanOutPut(console.output()), is("No commits on master since HH:mm:dd -- First commit\n"));
        assertThat(console.error(), is(""));
    }

    @Test
    public void testGT_status2(Console console) throws GitAPIException, IOException {
        git.checkout().setName("other").setCreateBranch(true).call();

        writeString(root.resolve("b.txt"), "b");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Second commit").call();

        writeString(root.resolve("b.txt"), "c", StandardOpenOption.TRUNCATE_EXISTING);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Third commit").call();

        git.checkout().setName("master").call();

        writeString(root.resolve("d.txt"), "d");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Fourth commit").call();

        git.checkout().setName("other").call();

        GitTool gt = new GitTool(console.tty(), env) {
            @Override
            protected void errorExit() {
            }
        };

        gt.execute("st");

        assertThat(cleanOutPut(console.output()), is(
                "Commits on other [+2,-1] since HH:mm:dd -- [d:master] First commit\n" +
                "+ Second commit (HH:mm:dd)\n" +
                "+ Third commit (HH:mm:dd)\n" +
                "- Fourth commit (HH:mm:dd)\n"));
        assertThat(console.error(), is(""));
    }


    @Test
    public void testGT_status3(Console console) throws GitAPIException, IOException {
        git.checkout().setName("other").setCreateBranch(true).call();

        writeString(root.resolve("b.txt"), "b");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Second commit").call();

        writeString(root.resolve("b.txt"), "c");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Third commit").call();

        git.checkout().setName("master").call();

        writeString(root.resolve("d.txt"), "d");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Fourth commit").call();

        git.checkout().setName("other").call();

        GitTool gt = new GitTool(console.tty(), env) {
            @Override
            protected void errorExit() {
            }
        };

        gt.execute("st", "-r");

        assertThat(cleanOutPut(console.output()),
                   is("""
                              Commits on other [+2,-1] since HH:mm:dd -- [d:master] First commit
                               A b.txt
                              """));
        assertThat(console.error(), is(""));
    }

    private String cleanOutPut(String out) {
        // TODO(morimekta): Remove the newline hack when utils are updated.
        return stripNonPrintable(out.replaceAll("\n", "________"))
                // Also remove date, as it will be constantly moving...
                .replaceAll("[\\d][\\d]:[\\d][\\d]:[\\d][\\d]", "HH:mm:dd")
                .replaceAll("________", "\n");
    }
}
