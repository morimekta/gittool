import os, re

def get_diff_stats(file_a, file_b):
    """Get diff details for the file pair.

    return {(added, deleted, modified)} A 3-tuple with diff information.
    """
    f = os.popen('diff -e %s %s' % (file_a, file_b))

    lines_added = 0
    lines_deleted = 0
    lines_modified = 0
    mode = 'E'  # assume next is ed command line.
    # mode 'a'  # added file content
    # mode 'c'  # changed content
    origin_lines = 0
    target_lines = 0
    for line in f:
        line = line.strip()
        if mode == 'E':
            c = line
            m = _ed_pattern.match(line.strip())
            if m != None:
                origin_lines = 0
                if m.group('toline') != None:
                    toline = int(m.group('toline')[1:])
                    line = int(m.group('line'))
                    origin_lines = toline - line
                op = m.group('op')
                mode = op
                if op != 'a':
                    origin_lines += 1
                if op == 'd':
                    lines_deleted += origin_lines
                    mode = 'E'
                    continue
                target_lines = 0
                continue
        elif line == '.':
            if mode == 'a':
                lines_added += target_lines
            if mode == 'c':
                if (origin_lines != target_lines):
                    diff = target_lines - origin_lines
                    if diff > 0:
                        lines_added += diff
                    else:
                        lines_deleted += (-diff)
                    lines_modified += min(origin_lines, target_lines)
                else:
                    lines_modified += origin_lines
            mode = 'E'
        else:
            target_lines += 1
    return lines_added, lines_deleted, lines_modified


def get_file_lines(file):
    """Get the number of lines in a file (for diff stats)."""
    f = os.popen('wc -l ' + file)
    lines = 0
    for l in f:
        lines = int(l.strip())
    return lines


def parse_diff_status(command):
    """Parse the result for file diff.
    - command {str} The command to fetch the diff.
    """
    cmd = []
    cmd.append(command)
    cmd.append('egrep \'^\+*(\+\+\+|\-\-\-|Binary files) \'')

    f = os.popen(' | '.join(cmd))
    status = dict()
    a = None
    for line in f:
        line = line.strip()
        _a, _b = None, None
        m = _binary_files_pattern.match(line)
        if m != None:
            _a = _ab_prefix_pattern.sub('', m.group('a'))
            _b = _ab_prefix_pattern.sub('', m.group('b'))
        elif _a_pattern.search(line) != None:
            a = _a_pattern.sub('', line)
            continue
        else:
            _a = a
            _b = _b_pattern.sub('', line)

        stat = None
        file = None
        if _a == '/dev/null':
            stat = 'A'
            file = _b
        elif _b == '/dev/null':
            stat = 'D'
            file = _a
        else:
            stat = 'M'
            file = _a
        a = None
        if file == None:
            continue
        status[file] = stat
    return status


""" ------------------------------  PRIVATE  ------------------------------ """

_ed_pattern = re.compile('^(?P<line>[0-9]*)(?P<toline>,[0-9]*)?(?P<op>[adc])$')

_binary_files_pattern = re.compile('Binary files (?P<a>.*) and (?P<b>.*) differ')
_ab_prefix_pattern = re.compile('^[ab]/')
_a_pattern = re.compile('^--- (a/)?')
_b_pattern = re.compile('^\+\+\+ (b/)?')
