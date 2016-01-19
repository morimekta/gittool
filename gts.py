#!/usr/bin/python
import sys, os, re, argparse

from gittool.gitignore import ignore_file
from utils.char import color
from utils.color import RED, YELLOW
from utils.console import warn
from utils.strutil import ljust, hilite


class Match(object):
    def __init__(self, filename, num, line):
        self.filename = filename
        self.num = num
        self.line = line


def main(argv):
    __fparser = argparse.ArgumentParser(
            prog=argv[0],
            description='Search in files and get structured output.')
    __fparser.add_argument('-f', '--file',
                           type=re.compile,
                           help='Pattern to match file names.')
    __fparser.add_argument('-p', '--path',
                           action='append',
                           help='File path to search within. Can be repeated.')
    __fparser.add_argument('--tab',
                           type=str,
                           default='  ',
                           help='Tab replacement.')
    __fparser.add_argument('-i', '--ignorecase',
                           action='store_true',
                           help='Search case insensitive')
    __fparser.add_argument('pattern',
                           type=re.compile,
                           nargs='+',
                           help='The search patterns.')

    flags = None
    try:
        flags = __fparser.parse_args(argv[1:])
    except Exception as ex:
        warn(str(ex))
        print ''
        __fparser.print_help()
        exit(1)

    command = [
      'grep',
      '-n',  # Show line number
      '-R',  # Recursive
    ]

    re_arg = 0
    if flags.ignorecase:
        command.append('-i')
        re_arg = re.I
    for pattern in flags.pattern:
        p = re.sub(r'(["\'\\])', r'\\\1', pattern.pattern)
        command.append('-e "%s"' % p)

    if flags.path is not None and len(flags.path) > 0:
        for path in flags.path:
            command.append(path)
    else:
        command.append('*')

    cmd = [
      ' '.join(command),
      'grep -v -e "^Bin.r fil .* samsvarer$" -e "^Binary file .* matches$"',
    ]

    grep = os.popen(' | '.join(cmd))

    results = []
    longest_file = 0

    replace = color(RED) + '\\1' + color(0)

    for line in grep:
        match = __line_pattern.match(line)
        if match is None:
            print "Error parsing line: " + line
            continue
        filename = match.group('file')
        if ignore_file(filename):
            continue
        if flags.file is not None and flags.file.search(filename) is None:
            continue
        longest_file = max(longest_file, len(filename))
        results.append(Match(
                filename,
                int(match.group('num')),
                color_line(
                    flags.pattern, replace, match.group('line'), re_arg),
                ))

    for match in results:
        filename = ljust(hilite(match.filename, color(YELLOW)), longest_file + 1)
        line = match.line.replace('\t', flags.tab)
        print '%s:%4d :%s' % (filename, match.num, line)


def color_line(patterns, replace, line, re_arg):
    for p in patterns:
        pat = re.compile('(' + p.pattern + ')', flags=re_arg)
        line = pat.sub(replace, line)
    return line


__line_pattern = re.compile(r'(?P<file>[^:]*):(?P<num>[\d]*):(?P<line>.*)$')


if __name__ == '__main__':
    main(sys.argv)
