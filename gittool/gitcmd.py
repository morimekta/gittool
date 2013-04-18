import re, os, subprocess

import argparse

from gittool import gitconfig
from gittool import gitcore
from gittool import gitdiff
from gittool import gitutil
from gittool.difftool import *
from utils import char
from utils import cmd
from utils import color
from utils import console
from utils import interactive
from utils import timeutil
from utils import strutil


class Diff(cmd.Command):
    def __init__(self):
        super(Diff, self).__init__('diff', 'Shows file differences.')
        self.__parser = argparse.ArgumentParser(
                prog='gt diff',
                description="""
                        Shows file differences with advanced selection
                        options.""",
                usage='%(prog)s [options] [--] [files ...\]')
        self.__path = None
        self.__diff = DiffTool(lambda: self.__path, self.__parser)

        group = self.__parser.add_mutually_exclusive_group()
        group.add_argument('-b', '--branch', type=str,
                help='Show diff agsint against HEAD of given branch instead ' +
                     'of current.')
        group.add_argument('-g', '--git', action='store_true',
                help='Show diff against the last commit of current branch.')
        group.add_argument('-A', '--ancestor', type=str,
                help='Show diff against the common ancestor of given branch.',
                default=None)
        group.add_argument('-L', '--location', type=str,
                help='Show diff against against files in a random location.')
        group.add_argument('-r', '--remote', action="store_true",
                help='Show diff against against files in the tracked remote branch.')

    def run(self, argv):
        self.__flags = self.__parser.parse_args(argv)
        self.__path = gitdiff.GitPath('.gt')

        provider = None
        current = gitcore.current_branch()
        if self.__flags.branch != None:
            provider = gitdiff.GitProvider(lambda: self.__path,
                    gitcore.current_revision(self.__flags.branch))
        elif self.__flags.location != None:
            provider = LocalDiffProvider(lambda: self.__path,
                    self.__flags.location)
        elif self.__flags.git:
            provider = gitdiff.GitProvider(lambda: self.__path,
                    include_untracked=True)
        elif self.__flags.remote:
            remote = gitconfig.get_tracking(current)
            if remote == None:
                raise Exception('No remote tracking branch.')
            provider = gitdiff.GitProvider(lambda: self.__path,
                    revision=gitcore.current_revision(remote))
        else:
            if self.__flags.ancestor == None:
                self.__flags.ancestor = gitutil.get_verified_diffbase(current, False, True)
            provider = gitdiff.GitProvider(lambda: self.__path,
                    gitcore.common_ancestor(self.__flags.ancestor))

        self.__diff.run(self.__flags, provider)

    def help(self):
        self.__parser.print_help()

class Status(cmd.Command):
    __ancestor = char.color(color.YELLOW)
    __git = char.color(color.RED)

    def __init__(self, prog='gt status'):
        super(Status, self).__init__('status', 'Show the current branch status.')
        self.__parser = argparse.ArgumentParser(prog=prog,
                description='Show the current branch status.')
        self.__parser.add_argument('-b', '--branch', type=str,
                default=None,
                help='Branch to to find common merge ancestor.')
        self.__parser.add_argument('-r', '--relative', action='store_true',
                help='Show relative paths instead of repository paths.')
        self.__path = None

    def run(self, argv):
        self.__flags = self.__parser.parse_args(argv)
        self.__path = gitdiff.GitPath('.gt')

        current = gitcore.current_branch()

        if self.__flags.branch == None:
            self.__flags.branch = gitutil.get_verified_diffbase(current, False, True)

        ancestor = gitcore.common_ancestor(self.__flags.branch)
        commit = gitcore.current_revision()
        revision = gitcore.revision_info(ancestor)
        if ancestor != commit:
            status = diffutil.parse_diff_status('git diff --cc ' +
                    ancestor + ' ' + commit)
            dmesg = ''
            if self.__flags.branch is not gitconfig.master():
                    dmesg = '[d:%s%s%s] ' % (
                    char.color(color.YELLOW, color.DIM), self.__flags.branch,
                    char.color(0))
            message = 'Changes on %s%s%s since %s -- %s%s%s%s' % (
                char.color(color.YELLOW, color.BOLD), current, char.color(0),
                timeutil.format_short(revision.datetime), dmesg,
                char.color(color.DIM), revision.message, char.color(0))
            self.__show_status(status, self.__ancestor, message)
        else:
            console.notify('%sNo changes%s on %s%s%s since %s -- %s%s%s' % (
                char.color(color.YELLOW, color.BOLD), char.color(0),
                char.color(color.YELLOW), current, char.color(0),
                timeutil.format_short(revision.datetime),
                char.color(color.DIM), revision.message, char.color(0)))

        provider = gitdiff.GitProvider(lambda: self.__path,
                include_untracked=True)
        status = provider.status()

        if len(status) > 0:
            # TODO(steineldar): Split in two groups, staged and unstaged, and
            # make it clear which is which (use color)?
            message = 'Local changes in your git client:'
            self.__show_status(status, self.__git, message)

    def help(self):
        print self.__parser.print_help()

    def show_status(self, status, col, info):
        self.__show_status(status, col, info)

    def __show_status(self, status, col, info):
        r, c = console.terminal_size()
        print strutil.trimwidth(info, c)
        files = status.keys()
        files.sort()
        for file in files:
            display = self.__path.display_path(file, self.__flags.relative)
            st = '   '
            if status[file] != 'M':
                st = '%-2s ' % status[file].replace('-', ' ')
            print '%s%s%s%s' % (st, col, display, char.color(0))


""" -------------------------- PRIVATE ---------------------- """


class _Repo(object):
    def __init__(self, name, url):
        self.name = name
        self.url = url

__git_help_instance = None


def _get_git_help():
    """Get the git help message dict.

    return {dict(str: str)} Dictionary from command name to short help message.
    """
    global __git_help_instance
    if __git_help_instance == None:
        __git_help_instance = dict()
        pattern = re.compile(r'[\s]*(?P<cmd>[a-z]+)[\s]+(?P<desc>.*)')
        hlp = os.popen('git help')
        for line in hlp:
            match = pattern.match(line)

            if match == None:
                continue

            cmd = match.group('cmd')
            desc = match.group('desc')

            if cmd is not None and desc is not None:
                __git_help_instance[cmd] = desc + ' [git ' + cmd + ']'

    return __git_help_instance
