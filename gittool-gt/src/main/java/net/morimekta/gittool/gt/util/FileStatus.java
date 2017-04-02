package net.morimekta.gittool.gt.util;

import net.morimekta.console.chr.Color;

import org.eclipse.jgit.diff.DiffEntry;

import java.nio.file.Paths;

import static net.morimekta.console.chr.Color.BOLD;
import static net.morimekta.console.chr.Color.CLEAR;
import static net.morimekta.console.chr.Color.DIM;
import static net.morimekta.console.chr.Color.GREEN;
import static net.morimekta.console.chr.Color.RED;
import static net.morimekta.console.chr.Color.YELLOW;
import static net.morimekta.gittool.gt.GitTool.pwd;

/**
 * The status of a single change in diff.
 *
 * - staged git
 * - unstaged git
 */
public class FileStatus {
    private final String    root;
    private final boolean   relative;

    private DiffEntry staged;
    private DiffEntry unstaged;

    public FileStatus(boolean relative, String root, DiffEntry un) {
        this.relative = relative;
        this.root = root;
        this.unstaged = un;
    }

    public FileStatus setStaged(DiffEntry staged) {
        this.staged = staged;
        return this;
    }

    public DiffEntry.ChangeType getOverallChange() {
        if (staged == null) {
            return unstaged.getChangeType();
        } else if (unstaged == null) {
            return staged.getChangeType();
        } else {
            if (unstaged.getChangeType() == DiffEntry.ChangeType.DELETE) {
                return DiffEntry.ChangeType.DELETE;
            }
            if (staged.getChangeType() == DiffEntry.ChangeType.ADD) {
                return DiffEntry.ChangeType.ADD;
            }
            if (getNewestPath().equals(getOldestPath())) {
                return DiffEntry.ChangeType.MODIFY;
            }
            // With if the file was copies at leat *once*, then i
            if (staged.getChangeType() == DiffEntry.ChangeType.COPY ||
                unstaged.getChangeType() == DiffEntry.ChangeType.COPY) {
                return DiffEntry.ChangeType.COPY;
            }
            return DiffEntry.ChangeType.RENAME;
        }
    }

    public String stagedMod() {
        if (staged == null) {
            switch (unstaged.getChangeType()) {
                case COPY:
                    return " C";
                case DELETE:
                    return " D";
                case ADD:
                    return "??";  // untracked
                case RENAME:
                    return " R";
                case MODIFY:
                    return " M";
                default:
                    return "  ";
            }
        } else if (unstaged == null) {
            switch (staged.getChangeType()) {
                case COPY:
                    return "C ";
                case DELETE:
                    return "D ";
                case ADD:
                    return "A ";
                case RENAME:
                    return "R ";
                case MODIFY:
                    return "  ";
                default:
                    return "  ";
            }
        } else {
            // both...
            StringBuilder builder = new StringBuilder();
            switch (staged.getChangeType()) {
                case COPY:
                    builder.append('C');
                    break;
                case DELETE:
                    builder.append('D');
                    break;
                case ADD:
                    builder.append('A');
                    break;
                case RENAME:
                    builder.append('R');
                    break;
                case MODIFY:
                    builder.append('M');
                    break;
                default:
                    builder.append(' ');
                    break;
            }
            switch (unstaged.getChangeType()) {
                case COPY:
                    builder.append('C');
                    break;
                case DELETE:
                    builder.append('D');
                    break;
                case ADD:
                    builder.append('A');
                    break;
                case RENAME:
                    builder.append('R');
                    break;
                case MODIFY:
                    builder.append('M');
                    break;
                default:
                    builder.append(' ');
                    break;
            }
            return builder.toString();
        }
    }

    private static boolean notNullPath(String path) {
        return path != null && !"/dev/null".equals(path);
    }

    public String getOldestPath() {
        if (this.staged != null) {
            if (notNullPath(this.staged.getOldPath())) {
                return this.staged.getOldPath();
            }
            return this.staged.getNewPath();
        }
        if (notNullPath(this.unstaged.getOldPath())) {
            return this.unstaged.getOldPath();
        }
        return this.unstaged.getNewPath();
    }

    public String getNewestPath() {
        if (this.unstaged != null) {
            if (notNullPath(this.unstaged.getNewPath())) {
                return this.unstaged.getNewPath();
            }
            return this.unstaged.getOldPath();
        }
        if (notNullPath(this.staged.getNewPath())) {
            return this.staged.getNewPath();
        }
        return this.staged.getOldPath();
    }

    public String statusLine() {
        StringBuilder builder = new StringBuilder();
        builder.append(stagedMod());
        builder.append(' ');

        switch (getOverallChange()) {
            case ADD:
                builder.append(new Color(GREEN, BOLD));
                break;
            case DELETE:
                builder.append(new Color(RED, BOLD));
                break;
            case RENAME:
            case COPY:
                builder.append(new Color(YELLOW, DIM));
                break;
            case MODIFY:
                builder.append(YELLOW);
                break;
        }

        builder.append(path(getNewestPath()));
        builder.append(CLEAR);

        if (!getNewestPath().equals(getOldestPath())) {
            builder.append(" <- ");
            builder.append(DIM);
            builder.append(path(getOldestPath()));
            builder.append(CLEAR);
        }

        return builder.toString();
    }

    private String path(String path) {
        if (relative) {
            return pwd.relativize(Paths.get(root, path)).toString();
        }
        return path;
    }
}
