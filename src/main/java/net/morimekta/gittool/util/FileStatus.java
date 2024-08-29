package net.morimekta.gittool.util;

import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.diff.DiffEntry;

import java.nio.file.Path;

import static net.morimekta.gittool.GitTool.pwd;
import static net.morimekta.strings.chr.Color.*;

/**
 * The status of a single change in diff.
 * <p>
 * - staged git
 * - unstaged git
 */
public class FileStatus {
    private final Path root;
    private final boolean relative;

    private DiffEntry staged;
    private DiffEntry unstaged;

    public FileStatus(boolean relative, Path root, DiffEntry un) {
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
            // With if the file was copies at least *once*, then it is copied.
            if (staged.getChangeType() == DiffEntry.ChangeType.COPY ||
                    unstaged.getChangeType() == DiffEntry.ChangeType.COPY) {
                return DiffEntry.ChangeType.COPY;
            }
            return DiffEntry.ChangeType.RENAME;
        }
    }

    public String stagedMod() {
        if (staged == null) {
            return switch (unstaged.getChangeType()) {
                case COPY -> " C";
                case DELETE -> " D";
                case ADD -> "??";  // untracked
                case RENAME -> " R";
                case MODIFY -> " M";
            };
        } else if (unstaged == null) {
            return switch (staged.getChangeType()) {
                case COPY -> "C ";
                case DELETE -> "D ";
                case ADD -> "A ";
                case RENAME -> "R ";
                case MODIFY -> "  ";
            };
        } else {
            return "" + stageChangeLetter(staged.getChangeType())
                    + stageChangeLetter(unstaged.getChangeType());
        }
    }

    private char stageChangeLetter(DiffEntry.ChangeType unstaged) {
        return switch (unstaged) {
            case COPY -> 'C';
            case DELETE -> 'D';
            case ADD -> 'A';
            case RENAME -> 'R';
            case MODIFY -> 'M';
        };
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
            return pwd.relativize(root.resolve(path)).toString();
        }
        return path;
    }
}
