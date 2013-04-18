import sys, os, re, argparse

from gittool.gitignore import ignore_file
from utils.char import color
from utils.color import RED, YELLOW
from utils.console import warn
from utils.strutil import ljust, hilite

def print_help():
    __flags.print_help()

class Match(object):
    def __init__(self, file, num, line):
        self.file = file
        self.num = num
        self.line = line

def main(argv):
    __fparser = argparse.ArgumentParser(prog=argv[0],
            description='Search in files and get structured output.')
    __fparser.add_argument('-f', '--file', type=re.compile,
            help='Pattern to match file names.')
    __fparser.add_argument('-p', '--path', action='append',
            help='File path to search within. Can be repeated.')
    __fparser.add_argument('--tab', type=str, default='  ',
            help='Tab replacement.')
    __fparser.add_argument('-i', '--ignorecase', action='store_true',
            help='Search case insensitive')

    __fparser.add_argument('pattern', type=re.compile, nargs='+',
            help='The search patterns.')

    flags = None
    try:
        flags = __fparser.parse_args(argv[1:])
    except Exception as ex:
        warn(str(ex))
        print ''
        __fparser.print_help()
        exit(1)


    command = []
    command.append('grep')
    command.append('-n')  # Show line number
    command.append('-R')  # Recursive

    re_arg = 0
    if flags.ignorecase:
        command.append('-i')
        re_arg = re.I
    for pattern in flags.pattern:
        p = re.sub(r'(["\'\\])', r'\\\1', pattern.pattern)
        command.append('-e "%s"' % p)

    if flags.path != None and len(flags.path) > 0:
        for path in flags.path:
            command.append(path)
    else:
        command.append('*')

    cmd = []
    cmd.append(' '.join(command))
    cmd.append('grep -v "^Bin.r fil .* samsvarer$"')
    cmd.append('grep -v "^Binary file .* matches$"')

    grep = os.popen(' | '.join(cmd))

    results = []
    longestfile = 0

    replace = color(RED) + '\\1' + color(0)

    for line in grep:
        match = __linepattern.match(line)
        if match == None:
            print "Error parsing line: " + line
            continue
        file = match.group('file')
        if ignore_file(file):
            continue
        if flags.file != None and flags.file.search(file) == None:
            continue
        longestfile = max(longestfile, len(file))
        results.append(Match(
                file = file,
                num  = int(match.group('num')),
                line = color_line(
                    flags.pattern, replace, match.group('line'), re_arg),
                ))

    for match in results:
        file = ljust(hilite(match.file, color(YELLOW)), longestfile + 1)
        line = match.line.replace('\t', flags.tab)
        print '%s:%4d :%s' % (file, match.num, line)

def color_line(patterns, replace, line, re_arg):
    for p in patterns:
        pat = re.compile('(' + p.pattern + ')', flags=re_arg)
        line = pat.sub(replace, line)
    return line


__linepattern = re.compile(r'(?P<file>[^:]*):(?P<num>[\d]*):(?P<line>.*)$')


if __name__ == '__main__':
    main(sys.argv)
