package net.morimekta.gittool.util;

import net.morimekta.collect.util.LazyCachedSupplier;
import net.morimekta.gittool.GitTool;
import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;

import static net.morimekta.collect.UnmodifiableList.asList;
import static net.morimekta.collect.util.LazyCachedSupplier.lazyCache;
import static net.morimekta.gittool.util.Colors.RED_BOLD;
import static net.morimekta.gittool.util.Colors.YELLOW_DIM;
import static net.morimekta.gittool.util.Utils.addsAndDeletes;
import static net.morimekta.gittool.util.Utils.clr;
import static net.morimekta.strings.StringUtil.rightPad;
import static net.morimekta.strings.chr.Color.BG_BLUE;
import static net.morimekta.strings.chr.Color.BLUE;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.YELLOW;

public class BranchInfo implements Comparable<BranchInfo> {
    private final String name;

    private final LazyCachedSupplier<RevCommit> commit;
    private final LazyCachedSupplier<Boolean>   isCurrent;
    private final LazyCachedSupplier<Boolean>   isDefault;
    private final LazyCachedSupplier<Boolean>   hasUncommitted;

    private final LazyCachedSupplier<String>              diffBase;
    private final LazyCachedSupplier<Boolean>             diffBaseIsDefault;
    private final LazyCachedSupplier<Optional<Ref>>       diffBaseRef;
    private final LazyCachedSupplier<Optional<RevCommit>> diffBaseCommit;

    private final LazyCachedSupplier<Optional<String>>    remote;
    private final LazyCachedSupplier<Boolean>             remoteIsGone;
    private final LazyCachedSupplier<Optional<Ref>>       remoteRef;
    private final LazyCachedSupplier<Optional<RevCommit>> remoteCommit;

    private final LazyCachedSupplier<Integer> localCommits;
    private final LazyCachedSupplier<Integer> missingCommits;

    public BranchInfo(Ref currentRef, GitTool gt) {
        this.name = currentRef.getName().startsWith("refs/heads/")
                    ? currentRef.getName().substring(11)
                    : currentRef.getName();

        this.isCurrent = lazyCache(() -> {
            try {
                return currentRef.getName().equals(gt.getRepository().getFullBranch());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        this.isDefault = lazyCache(() -> name.equals(gt.defaultBranch.get()));
        this.hasUncommitted = lazyCache(() -> {
            try {
                if (isCurrent()) {
                    var hasCached = !gt
                            .getGit().diff()
                            .setShowNameAndStatusOnly(true)
                            .setCached(true)
                            .call().isEmpty();
                    var hasUncached = gt
                            .getGit().diff()
                            .setShowNameAndStatusOnly(true)
                            .setCached(false)
                            .call()
                            .stream()
                            .anyMatch(i -> {
                                if (i.getChangeType() == DiffEntry.ChangeType.DELETE) {
                                    try {
                                        var path = gt.getRepositoryRoot().resolve(i.getOldPath());
                                        if (Files.isDirectory(path)) {
                                            // Weirdness where it reports empty folders that are checked
                                            // in as a folder as deleted.
                                            return false;
                                        }
                                    } catch (IOException e) {
                                        return true;
                                    }
                                }
                                return true;
                            });

                    return hasCached || hasUncached;
                } else {
                    return false;
                }
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
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
                if (isDefault() && remote() != null) {
                    return remote();
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
        this.remoteCommit = lazyCache(() -> remoteRef.get().map(ref -> {
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
        this.remoteIsGone = lazyCache(() -> remoteCommit.get().isEmpty());

        // -----------------

        this.localCommits = lazyCache(() -> {
            if (diffBase().equals(name)) return 0;
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return asList(gt.getGit().log().addRange(diffWithHead, currentHead).call()).size();
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.missingCommits = lazyCache(() -> {
            if (diffBase().equals(name)) return 0;
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().map(Ref::getObjectId).orElse(null);
            if (diffWithHead == null || diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return asList(gt.getGit().log().addRange(currentHead, diffWithHead).call()).size();
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

    public RevCommit remoteCommit() {
        return remoteCommit.get().orElse(null);
    }

    // --------------

    public int localCommits() {
        return localCommits.get();
    }

    public int missingCommits() {
        return missingCommits.get();
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
            builder.append(" ").append(addsAndDeletes(localCommits(), missingCommits(), baseColor));
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
