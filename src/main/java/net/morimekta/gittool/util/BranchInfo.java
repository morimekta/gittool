package net.morimekta.gittool.util;

import net.morimekta.collect.util.LazyCachedSupplier;
import net.morimekta.gittool.GitTool;
import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import java.io.IOException;

import static net.morimekta.collect.UnmodifiableList.asList;
import static net.morimekta.collect.util.LazyCachedSupplier.lazyCache;
import static net.morimekta.strings.StringUtil.rightPad;
import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.CLEAR;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;
import static net.morimekta.strings.chr.Color.YELLOW;

public class BranchInfo implements Comparable<BranchInfo> {
    private final String name;

    private final LazyCachedSupplier<Boolean> isCurrent;
    private final LazyCachedSupplier<Boolean> isDefault;
    private final LazyCachedSupplier<Boolean> hasUncommitted;
    private final LazyCachedSupplier<String>  diffBase;
    private final LazyCachedSupplier<String>  remote;
    private final LazyCachedSupplier<Boolean> remoteGone;

    private final LazyCachedSupplier<Ref>     diffBaseRef;
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
                throw new RuntimeException(e);
            }
        });
        this.isDefault = lazyCache(() -> name.equals(gt.defaultBranch.get()));
        this.hasUncommitted = lazyCache(() -> {
            try {
                return !gt.getGit().diff()
                          .setShowNameAndStatusOnly(true)
                          .setCached(true)
                          .call().isEmpty() ||
                       !gt.getGit().diff()
                          .setShowNameAndStatusOnly(true)
                          .setCached(false)
                          .call().isEmpty();
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.remote = lazyCache(() -> gt.getRemote(name));
        this.remoteGone = lazyCache(() -> {
            if (remote.get() == null) {
                return false;
            }
            try {
                return gt.commitOf(remote.get()).isEmpty();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.diffBase = lazyCache(() -> gt.getDiffbase(name));
        this.diffBaseRef = lazyCache(() -> {
            try {
                return gt.getRepository().getRefDatabase().findRef(gt.refName(diffBase.get()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.localCommits = lazyCache(() -> {
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().getObjectId();
            if (diffWithHead.equals(currentHead)) {
                return 0;
            }
            try {
                return asList(gt.getGit().log().addRange(diffWithHead, currentHead).call()).size();
            } catch (GitAPIException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.missingCommits = lazyCache(() -> {
            ObjectId currentHead = currentRef.getObjectId();
            ObjectId diffWithHead = diffBaseRef.get().getObjectId();
            if (diffWithHead.equals(currentHead)) {
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

    public String diffBase() {
        return diffBase.get();
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

    public int localCommits() {
        return localCommits.get();
    }

    public int missingCommits() {
        return missingCommits.get();
    }

    // --------------

    public String branchLine(Color baseColor, int longestBranchName, int longestRemoteName) {
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
        if (diffBase().equals(name)) {
            builder.append("    ")
                   .append(" ".repeat(longestRemoteName));
        } else if (remote.get() != null) {
            builder.append(" <- ")
                   .append(DIM)
                   .append(rightPad(remote.get(), longestRemoteName));
            clr(builder, baseColor);
        } else {
            builder.append(" d: ")
                   .append(new net.morimekta.strings.chr.Color(YELLOW, DIM))
                   .append(rightPad(diffBase(), longestRemoteName));
            clr(builder, baseColor);
        }

        if (localCommits() > 0 || missingCommits() > 0) {
            builder.append(" [");
            if (localCommits() == 0) {
                builder.append(CLR_SUBS).append("-").append(missingCommits());
                clr(builder, baseColor);
            } else if (missingCommits() == 0) {
                builder.append(CLR_ADDS).append("+").append(localCommits());
                clr(builder, baseColor);
            } else {
                builder.append(CLR_ADDS).append("+").append(localCommits());
                clr(builder, baseColor);
                builder.append(",")
                       .append(CLR_SUBS).append("-").append(missingCommits());
                clr(builder, baseColor);
            }

            if (remote.get() != null && !diffBase().equals(remote.get())) {
                builder.append(",")
                       .append(BOLD)
                       .append("%%");
                clr(builder, baseColor);
            }
            if (remoteGone.get()) {
                builder.append(DIM)
                       .append(" gone");
                clr(builder, baseColor);
            }

            builder.append("]");
        }

        if (hasUncommitted.get()) {
            builder.append(" -- ")
                   .append(new net.morimekta.strings.chr.Color(BOLD, RED))
                   .append("MOD");
            clr(builder, baseColor);
            builder.append(" --");
        }

        return builder.toString();

    }

    public String selectionLine(Color baseColor) {
        StringBuilder builder = new StringBuilder();

        builder.append(DIM);
        builder.append(name);
        builder.append(CLEAR);
        if (baseColor != null) {
            builder.append(baseColor);
        }
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

    // --------------

    private static final Color CLR_ADDS = new Color(Color.GREEN, Color.BOLD);
    private static final Color CLR_SUBS = new Color(Color.RED, Color.BOLD);

    private static void clr(StringBuilder builder, Color baseColor) {
        builder.append(CLEAR);
        if (baseColor != null) {
            builder.append(baseColor);
        }
    }
}
