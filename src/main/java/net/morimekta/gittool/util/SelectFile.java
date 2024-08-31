package net.morimekta.gittool.util;

import net.morimekta.strings.chr.Color;
import org.eclipse.jgit.diff.DiffEntry;

import static net.morimekta.gittool.util.Utils.clr;
import static net.morimekta.strings.chr.Color.BOLD;
import static net.morimekta.strings.chr.Color.DIM;
import static net.morimekta.strings.chr.Color.GREEN;
import static net.morimekta.strings.chr.Color.RED;

public class SelectFile {
    public final DiffEntry entry;
    public boolean selected = false;

    public SelectFile(DiffEntry entry) {
        this.entry = entry;
    }

    public String displayLine(Color baseColor) {
        StringBuilder sb = new StringBuilder();
        sb.append(" [");
        if (selected) {
            sb.append('x');
        } else {
            sb.append(' ');
        }
        sb.append("]: ");
        var sel = selected ? BOLD : DIM;
        switch (entry.getChangeType()) {
            case ADD:
                sb.append(GREEN).append("+ ").append(sel).append(entry.getNewPath());
                break;
            case DELETE:
                sb.append(RED).append("- ").append(sel).append(entry.getOldPath());
                break;
            case RENAME:
                sb.append("R ").append(sel).append(entry.getNewPath());
                break;
            case COPY:
                sb.append("C ").append(sel).append(entry.getNewPath());
                break;
            case MODIFY:
                sb.append("  ").append(sel).append(entry.getNewPath());
                break;
        }
        clr(sb, baseColor);
        return sb.toString();
    }
}
