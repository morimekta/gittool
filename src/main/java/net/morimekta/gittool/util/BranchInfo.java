package net.morimekta.gittool.util;

import net.morimekta.gittool.GitTool;
import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Supplier;

import static net.morimekta.collect.util.LazyCachedSupplier.lazyCache;
import static net.morimekta.gittool.util.Colors.RED_BOLD;
import static net.morimekta.gittool.util.Colors.YELLOW_DIM;
import static net.morimekta.gittool.util.Utils.addsAndDeletes;
import static net.morimekta.gittool.util.Utils.clr;
import static net.morimekta.gittool.util.Utils.countIter;
import static net.morimekta.strings.StringUtil.rightPad;
import static net.morimekta.strings.chr.Color.BG_BLUE;
import static net.morimekta.strings.chr.Color.BLUE;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.YELLOW;

public class BranchInfo implements Comparable<BranchInfo> {
    private final String name;

    private final Supplier<RevCommit> commit;
    private final Supplier<Boolean>   isCurrent;
    private final Supplier<Boolean>   isDefault;
    private final Supplier<Boolean>   hasUncommitted;

    private final Supplier<String>              diffBase;
    private final Supplier<Boolean>             diffBaseIsDefault;
    private final Supplier<Optional<Ref>>       diffBaseRef;
    private final Supplier<Optional<RevCommit>> diffBaseCommit;
    private final Supplier<Integer>             diffBaseLocalCommits;
    private final Supplier<Integer>             diffBaseMissingCommits;

    private final Supplier<Optional<String>> remote;
    private final Supplier<Boolean>          remoteIsGone;
    private final Supplier<Optional<Ref>>    remoteRef;
    private final Supplier<Integer>          remoteLocalCommits;
    private final Supplier<Integer>          remoteMissingCommits;

    public BranchInfo(Ref currentRef, GitTool gt) {
        this.name = currentRef
                .getName()
                .replaceAll("^refs/(heads|remotes)/", "");

        this.isCurrent = () -> {
            try {
                return currentRef.getName().equals(gt.getRepository().getFullBranch());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        this.isDefault = lazyCache(() -> name.equals(gt.defaultBranch.get()));
        this.hasUncommitted = lazyCache(() -> {
            if (isCurrent()) {
                return gt.hasUncommitted.get();
            } else {
                return false;
            }
        });
        this.commit = lazyCache(() -> {
            try {
                try (RevWalk revWalk = new RevWalk(gt.getRepository())) {
                    return revWalk.parseCommit(currentRef.getObjectId());
                } catch (MissingObjectException e) {
                    throw new UncheckedIOException(e);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // -----------------

        this.diffBase = lazyCache(() -> {
            try {
                var config = gt.getConfig();
                String tmp = config.getString("branch", name, "diffbase");
                if (tmp != null) {
                    return tmp;
                }
                return gt.defaultBranch.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        this.diffBaseIsDefault = lazyCache(() -> diffBase().equals(gt.defaultBranch.get()));
        this.diffBaseRef = lazyCache(() -> {
            try {
                return Optional.ofNullable(
                        gt.getRepository()
                          .getRefDatabase()
                          .findRef(gt.refName(diffBase.get())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        this.diffBaseCommit = lazyCache(() -> diffBaseRef.get().map(ref -> {
            try {
                try (RevWalk revWalk = new RevWalk(gt.getRepository())) {
                    return revWalk.parseCommit(ref.getObjectId());
                } catch (MissingObjectException e) {
                    return null;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
        this.diffBaseLocalCommits = lazyCache(() -> {
            if (diffBase().equals(name)) return 0;
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return countIter(gt.getGit().log().addRange(diffWithHead, currentHead).call());
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.diffBaseMissingCommits = lazyCache(() -> {
            if (diffBase().equals(name)) return 0;
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return countIter(gt.getGit().log().addRange(currentHead, diffWithHead).call());
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        // -----------------

        this.remote = lazyCache(() -> {
            try {
                var config = gt.getConfig();
                var remote = config.getString("branch", name, "remote");
                if (remote != null) {
                    var ref = config.getString("branch", name, "merge");
                    if (ref != null) {
                        if (ref.startsWith("refs/heads/")) {
                            return Optional.of(remote + "/" + ref.substring(11));
                        }
                    }
                }
                return Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        this.remoteRef = lazyCache(() -> remote.get().map(remote -> {
            try {
                return gt.getRepository().findRef(gt.refName(remote));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
        this.remoteIsGone = lazyCache(() -> remoteRef.get().isEmpty());
        this.remoteLocalCommits = lazyCache(() -> {
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = remoteRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return countIter(gt.getGit().log().addRange(diffWithHead, currentHead).call());
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.remoteMissingCommits = lazyCache(() -> {
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = remoteRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return countIter(gt.getGit().log().addRange(currentHead, diffWithHead).call());
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String name() {
        return name;
    }

    public boolean isCurrent() {
        return isCurrent.get();
    }

    public boolean isDefault() {
        return isDefault.get();
    }

    public boolean hasUncommitted() {
        return hasUncommitted.get();
    }

    public RevCommit commit() {
        return commit.get();
    }

    // --------------

    public String diffBase() {
        return diffBase.get();
    }

    public RevCommit diffBaseCommit() {
        return diffBaseCommit.get().orElse(null);
    }

    // --------------

    public String remote() {
        return remote.get().orElse(null);
    }

    // --------------

    public int localCommits() {
        return diffBaseLocalCommits.get();
    }

    public int missingCommits() {
        return diffBaseMissingCommits.get();
    }

    // --------------

    public String branchLine(Color baseColor, int longestBranchName) {
        StringBuilder builder = new StringBuilder();
        if (baseColor != null) {
            builder.append(baseColor);
        }
        if (isCurrent.get()) {
            builder.append("* ").append(GREEN);
        } else {
            builder.append("  ").append(YELLOW);
        }
        builder.append(rightPad(name, longestBranchName));
        clr(builder, baseColor);

        if (localCommits() > 0 || missingCommits() > 0) {
            builder.append(" ")
                   .append(addsAndDeletes(localCommits(), missingCommits(), baseColor));
        }

        if (isCurrent() && hasUncommitted.get()) {
            builder.append(" -- ").append(RED_BOLD).append("MOD");
            clr(builder, baseColor);
            builder.append(" --");
        }

        if (remote() != null) {
            if (remoteIsGone.get()) {
                builder.append(" gone: ").append(DIM);
            } else if (BG_BLUE.equals(baseColor)) {
                builder.append(" -> ");
            } else {
                builder.append(" -> ").append(BLUE);
            }
            builder.append(remote());
            clr(builder, baseColor);
            var local = remoteLocalCommits.get();
            var missing = remoteMissingCommits.get();
            if (local > 0 || missing > 0) {
                builder.append(" ")
                       .append(addsAndDeletes(local, missing, baseColor));
            }
        } else if (!diffBaseIsDefault.get()) {
            builder.append(" d: ");
            if (diffBase().equals(name)) {
                builder.append(DIM)
                       .append("<self>");
            } else {
                builder.append(YELLOW_DIM)
                       .append(diffBase());
            }
            clr(builder, baseColor);
        }

        builder.append(" ");
        builder.append(commit().getShortMessage());
        clr(builder, baseColor);

        return builder.toString();

    }

    public String selectionLine(Color baseColor) {
        StringBuilder builder = new StringBuilder();

        builder.append(DIM);
        builder.append(name);
        clr(builder, baseColor);
        return builder.toString();
    }

    // --------------

    @Override
    public int compareTo(BranchInfo o) {
        // Always put default branch first.
        if (isDefault()) {
            return -1;
        }
        if (o.isDefault()) {
            return 1;
        }
        return name.compareTo(o.name);
    }
}
