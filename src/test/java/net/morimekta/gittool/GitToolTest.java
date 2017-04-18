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

import net.morimekta.testing.rules.ConsoleWatcher;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static net.morimekta.console.chr.CharUtil.stripNonPrintable;
import static net.morimekta.testing.ResourceUtils.writeContentTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class GitToolTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public ConsoleWatcher console = new ConsoleWatcher()
            .withTerminalSize(44, 128);

    private File               root;
    private Map<String,String> env;
    private Git                git;

    @Before
    public void setUp() throws IOException, GitAPIException {
        root = tmp.newFolder("repo");
        env = new HashMap<>();
        env.putAll(System.getenv());
        env.put("PWD", root.getCanonicalFile().getAbsolutePath());

        git = Git.init().setDirectory(root).call();
        writeContentTo("a", new File(root, "a.txt"));

        git.add().addFilepattern(".").call();
        git.commit().setMessage("First commit").call();
    }

    @Test
    public void testGT_status1() {
        GitTool gt = new GitTool(Runtime.getRuntime(), console.tty(), env);

        gt.execute("st");

        assertThat(cleanOutPut(console.output()), is("No commits on master since HH:mm:dd -- First commit\n"));
        assertThat(console.error(), is(""));
    }

    @Test
    public void testGT_status2() throws GitAPIException {
        git.checkout().setName("other").setCreateBranch(true).call();

        writeContentTo("b", new File(root, "b.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Second commit").call();

        writeContentTo("c", new File(root, "b.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Third commit").call();

        git.checkout().setName("master").call();

        writeContentTo("d", new File(root, "d.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Fourth commit").call();

        git.checkout().setName("other").call();

        GitTool gt = new GitTool(Runtime.getRuntime(), console.tty(), env);

        gt.execute("st");

        assertThat(cleanOutPut(console.output()), is(
                "Commits on other [+2,-1] since HH:mm:dd -- [d:master] First commit\n" +
                "+ Second commit (HH:mm:dd)\n" +
                "+ Third commit (HH:mm:dd)\n" +
                "- Fourth commit (HH:mm:dd)\n"));
        assertThat(console.error(), is(""));
    }


    @Test
    public void testGT_status3() throws GitAPIException {
        git.checkout().setName("other").setCreateBranch(true).call();

        writeContentTo("b", new File(root, "b.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Second commit").call();

        writeContentTo("c", new File(root, "b.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Third commit").call();

        git.checkout().setName("master").call();

        writeContentTo("d", new File(root, "d.txt"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Fourth commit").call();

        git.checkout().setName("other").call();

        GitTool gt = new GitTool(Runtime.getRuntime(), console.tty(), env);

        gt.execute("st", "-r");

        assertThat(cleanOutPut(console.output()), is(
                "Commits on other [+2,-1] since HH:mm:dd -- [d:master] First commit\n" +
                " A b.txt\n"));
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
