import re, os

import argparse
import subprocess

from gittool import difftool
from gittool import gitconfig
from gittool import gitcore
from gittool import gitdiff
from gittool import gitutil
from utils import char
from utils import cmd
from utils import color
from utils import console
from utils import interactive
from utils import selection
from utils import timeutil

class Branch(cmd.Command):
    def __init__(self):
        super(Branch, self).__init__('branch', 'Change branch interactively.')
        self.__parser = argparse.ArgumentParser(prog='gt branch',
                description='Change branch interactively.')

        group = self.__parser.add_mutually_exclusive_group()
        group.add_argument('-a', '--all', action='store_true',
                help='Show all (interchangeable) branches.')
        group.add_argument('-r', '--remote', action='store_true',
                help='Show remote branches instead of local.')

        self.longest_branch_name = 0
        self.longest_remote_name = 0

        self.__branch = char.color(color.YELLOW)
        self.__ahead = char.color(color.BOLD, color.GREEN)
        self.__behind = char.color(color.BOLD, color.RED)
        self.__tracking = char.color(color.DIM, color.DEFAULT)
        self.__diffbase = char.color(color.DIM, color.YELLOW)
        self.__not_tracking = char.color(color.BOLD, color.DEFAULT)

    def get_parser(self):
        return self.__parser

    def init(self, flags):
        pass

    def get_extra_info(self, branch, colorset):
        extra = ''
        if branch.tracking != None:
            remote = branch.tracking.ljust(self.longest_remote_name)
            extra = ' <- %s%s%s%s' % (self.__tracking, remote, color.NONE, colorset)
        elif branch.diffbase is not gitconfig.master():
            remote = branch.diffbase.ljust(self.longest_remote_name)
            extra = '  d:%s%s%s%s' % (self.__diffbase, remote, color.NONE, colorset)
        else:
            extra = '    %s' % (' ' * self.longest_remote_name)

        ab = []
        if branch.ahead_ > 0:
            ab.append('+%s%i%s%s' % (self.__ahead, branch.ahead_, color.NONE, colorset))
        if branch.behind_ > 0:
            ab.append('-%s%i%s%s' % (self.__behind, branch.behind_, color.NONE, colorset))
        if len(ab) > 0:
            extra += ' (%s)' % ' '.join(ab)

        if branch.current and branch.is_modified_:
            extra += ' -- %sMOD%s%s --' % (self.__behind, color.NONE, colorset)

        return extra

    def display_string(self, branch, colorset):
        name = branch.branch.ljust(self.longest_branch_name)
        extra = self.get_extra_info(branch, colorset)

        if branch.current:
            return '* %s%s%s%s' % (colorset.green, name, colorset, extra)
        return '  %s%s%s%s' % (colorset.yellow, name, colorset, extra)

    def display_tracking(self, branch, colorset):
        name = branch.branch
        if name == None:
            name = '(None)'
        if branch.current:
            return '* %s%s%s' % (colorset.green, name, colorset)
        return '  %s%s%s' % (colorset.yellow, name, colorset)

    def get_commands(self):
        cmd = [
            selection.Select.Cmd('c', 'create',   self.__create_branch),
            selection.Select.Cmd('D', 'delete',   self.__delete_branch),
            selection.Select.Cmd('m', 'move',     self.__move_branch),
            selection.Select.Cmd('d', 'diffbase', self.__set_diffbase)]
        if not self.__flags.remote and not self.__flags.all:
            cmd.append(selection.Select.Cmd('t', 'track', self.__set_tracking))
        return cmd

    def __create_branch(self, branch):
        new_branch = interactive.getline(
                'Give name to the new branch:',
                char_verifier=gitutil.verify_branch_character,
                result_verifier=gitutil.verify_branch_name)
        if new_branch == None or len(new_branch) == 0:
            console.info('Aborting: Empty branch name given.')
            return selection.Select.Cmd.EXIT
        # TODO(steineldar): Don't print output from git checkout, but
        # check the stderr outut and print a fitting message.
        console.notify('')
        os.system('git checkout -b ' + new_branch)
        # We automatically switch to new branch.
        return selection.Select.Cmd.EXIT

    def __delete_branch(self, branch):
        if branch.current:
            console.warn('You cannot delete the current branch.')
            return selection.Select.Cmd.STAY

        name = branch.branch
        if interactive.confirm('Do you really want to delte the branch ' +
                repr(name) + '?'):
            cmd = []
            if branch.remote:
                origin, remote = branch.branch.split('/', 1)
                cmd = ['git', 'push', origin, '+:%s' % remote]
            else:
                cmd = ['git', 'branch', '-d', name]
            p = subprocess.Popen(cmd,
                    stderr=subprocess.PIPE, stdout=subprocess.PIPE)
            p.wait()
            success = True
            for l in p.stdout:
                pass
            for l in p.stderr:
                success = False
                console.warn(l.strip())
            if success:
                console.info('Deleted branch %s (was %s %s)' % (
                    repr(name), branch.revision[:7], branch.message))
                return selection.Select.Cmd.EXIT
            elif branch.remote:
                console.error('Unable to delete remote branch %s.' % branch.branch)
                return selection.Select.Cmd.EXIT
        else:
            return selection.Select.Cmd.EXIT

        if interactive.confirm('Do you still want to delte the branch?'):
            cmd = ['git', 'branch', '-D', name]
            p = subprocess.Popen(cmd,
                    stderr=subprocess.PIPE, stdout=subprocess.PIPE)
            p.wait()
            for l in p.stdout:
                pass
            for l in p.stderr:
                console.error(l.strip())
            else:
                console.info('Deleted branch %s (was %s %s)' % (
                    repr(name), branch.revision[:7], branch.message))
        return selection.Select.Cmd.EXIT

    def __move_branch(self, branch):
        console.warn('Moving branches not implemented (%s).' % branch.branch)
        return selection.Select.Cmd.EXIT

    def __set_diffbase(self, branch):
        diffbase = gitutil.get_verified_diffbase(branch.branch, False, False)

        if diffbase != None:
            # Must add newline and more to complete line. We are not strictly in
            # a consistent state here.
            # TODO(steineldar): Fix selection.Select() to handle cases like this.
            console.info('Changing diffbase from: %s%s%s\r\n' % (
                    self.__tracking, diffbase, color.NONE))

        remotes = [b for b in gitcore.branches() if b.branch != branch.branch]
        remotes.sort(lambda x, y: cmp(x.branch, y.branch))
        remotes.insert(0, gitcore.Branch(None))
  
        selected = -1
        for i in range(len(remotes)):
            if remotes[i].branch == branch.tracking:
                remotes[i].current = True
                selected = i

        select = selection.Select('Set diffbase for branch \'%s\' to:' % (branch.branch),
            items=remotes,
            mkline=self.display_tracking,
            current=selected)
        selected = select.select()
        if selected == None:
            return selection.Select.Cmd.EXIT
        if selected.branch == diffbase:
            if branch.diffbase == None:
                console.info('Branch already has no diffbase.')
            else:
                console.info('Branch already has \'%s\' as diffbase.' % branch.diffbase)
            return selection.Select.Cmd.EXIT

        gitconfig.set_diffbase(branch.branch, selected.branch)
        if selected.branch == None:
            console.info('Branch \'%s%s%s\' is %sno longer%s diffing against \'%s%s%s\'' % (
                    self.__branch, branch.branch, color.NONE,
                    self.__not_tracking, color.NONE,
                    self.__tracking, branch.tracking, color.NONE))
        else:
            console.info('Branch \'%s%s%s\' is now diffing against \'%s%s%s\'' % (
                    self.__branch, branch.branch, color.NONE,
                    self.__tracking, selected.branch, color.NONE))
        
        return selection.Select.Cmd.EXIT
            

    def __set_tracking(self, branch):
        if branch.tracking != None:
            # Must add newline and more to complete line. We are not strictly in
            # a consistent state here.
            # TODO(steineldar): Fix selection.Select() to handle cases like this.
            console.info('Changing tracked branch from: %s%s%s\r\n' % (
                    self.__tracking, branch.tracking, color.NONE))

        remotes = gitcore.remote_branches()
        remotes.sort(lambda x, y: cmp(x.branch, y.branch))
        remotes.insert(0, gitcore.Branch(None))

        selected = -1
        for i in range(len(remotes)):
            if remotes[i].branch == branch.tracking:
                remotes[i].current = True
                selected = i

        select = selection.Select('Set remote tracked branch for \'%s\' to:' % (branch.branch),
            items=remotes,
            mkline=self.display_tracking,
            current=selected)
        selected = select.select()
        if selected == None:
            return selection.Select.Cmd.EXIT
        if selected.branch == branch.tracking:
            if branch.tracking == None:
                console.info('Branch is already not tracking anything.')
            else:
                console.info('Branch is already tracking remote branch \'%s\'.' % branch.tracking)
            return

        gitconfig.set_tracking(branch.branch, selected.branch)

        if selected.branch == None:
            console.info('Branch \'%s%s%s\' is %sno longer%s tracking \'%s%s%s\'' % (
                    self.__branch, branch.branch, color.NONE,
                    self.__not_tracking, color.NONE,
                    self.__tracking, branch.tracking, color.NONE))
        else:
            console.info('Branch \'%s%s%s\' is now tracking \'%s%s%s\'' % (
                    self.__branch, branch.branch, color.NONE,
                    self.__tracking, selected.branch, color.NONE))

        return selection.Select.Cmd.EXIT

    def run(self, args):
        self.__flags = self.__parser.parse_args(args)
        self.init(self.__flags)

        branches = gitcore.branches()
        if self.__flags.all:
            branches += gitcore.parked_branches()
        elif self.__flags.remote:
            # Temporary just add, we still need to find the 'current' branch for some checks.
            branches += gitcore.remote_branches()
        branches.sort(lambda x, y: cmp(x.branch, y.branch))

        remote_branches = dict()
        remotes = gitcore.remote_branches()
        for r in remotes:
            self.longest_remote_name = max(self.longest_remote_name, len(r.branch))
            remote_branches[r.branch] = r

        items = []
        current = None
        selected = 0
        for i in range(len(branches)):
            branch = branches[i]
            self.longest_branch_name = max(self.longest_branch_name, len(branch.branch))
            if branch.current:
                current = branch
            if not self.__flags.remote or branch.remote:
                items.append(branch)
                if branch.current:
                    branch.is_modified_ = gitcore.has_modified_files(False)
                    selected = i
            branch.diffbase = gitutil.get_verified_diffbase(
                    branch.branch, False, True)
            if branch.diffbase != None:
                remote = gitcore.get_branch(branch.diffbase)
                branch.ahead_, branch.behind_ = gitcore.revision_diff(
                            branch.revision, remote.revision)
            elif branch.tracking != None:
                if remote_branches.has_key(branch.tracking):
                    remote = remote_branches[branch.tracking]
                    branch.ahead_, branch.behind_ = gitcore.revision_diff(
                            branch.revision, remote.revision)
                else:
                    branch.ahead_ = 0
                    branch.behind_ = 0

        cmd = self.get_commands()
        select = selection.Select('Move from branch \'%s\' to:' % (current),
            items=items,
            commands=cmd,
            mkline=self.display_string,
            current=selected)
        selected = select.select()
        if selected == None:
            return

        if branch.remote:
            console.error('Checking out remote branch not implemented.')
            return
        if gitcore.has_modified_files(False):
            console.error('Current branch contains modified files.')
            return

        os.system('git checkout ' + selected.branch)

    def help(self):
        self.__parser.print_help()


class NewCL(cmd.Command):
    def __init__(self):
        super(NewCL, self).__init__('newcl',
                'Select files from current branch to base new branch on.')
        self.__parser = argparse.ArgumentParser(prog='gt newcl',
                description='Select files from current branch to base new branch on.')

        self.__parser.add_argument('-f', '--files', metavar='F', nargs='*', type=str,
                help='Files to select for new branch.')
        self.__parser.add_argument('-b', '--branch', metavar='B', type=str,
                help='Branch to base changes on.',
                default=None)

    def __display_line(self, item, colorset):
        return '%-2s %s' % (item.status, item.name)

    def run(self, argv):
        self.__flags = self.__parser.parse_args(argv)
        self.__path = gitdiff.GitPath('.gt')

        if gitcore.has_modified_files(False):
            console.error('Current branch has modified files.')
            return

        current_branch = gitcore.current_branch()
        if self.__flags.branch == None:
          self.__flags.branch = gitconfig.get_diffbase(current_branch)

        provider = gitdiff.GitProvider(lambda: self.__path,
                gitcore.common_ancestor(self.__flags.branch))

        files = []
        if self.__flags.files and len(self.__flags.files) > 0:
            tmp_files = provider.status()
            for f in self.__flags.files:
                if tmp_files.has_key(f):
                    files.append(difftool.DiffTool.File(
                            f, None, self.__path.display_path(f, True), tmp_files[f]))
        else:
            tmp_files = provider.status()
            select_files = []
            for f in tmp_files.keys():
                select_files.append(difftool.DiffTool.File(
                    f, None, self.__path.display_path(f, True), tmp_files[f]))
            select_files.sort(lambda x, y: cmp(x.name, y.name))

            select = selection.MultiSelect(
                    'Select files to pass to new branch:', select_files,
                    mkline = self.__display_line)
            files = select.select()
            if files == None or len(files) == 0:
                console.info('No files selected.')
                return
        if len(files) == 0:
            console.info('No files to pass over to new branch.')
            return

        old_branch = gitcore.current_branch()
        new_branch = interactive.getline(
                'Give name to the new branch:',
                char_verifier=gitutil.verify_branch_character,
                result_verifier=gitutil.verify_branch_name)
        if new_branch == None or len(new_branch) == 0:
            console.info('Aborting: Empty branch name given.')

        if os.system('git checkout %s > /dev/null' % self.__flags.branch) != 0:
            return
        if os.system('git checkout -b %s > /dev/null ' % new_branch) != 0:
            return

        for f in files:
            if f.status == 'D':
                os.system('rm %s' % f.local)
            else:
                os.system('git checkout %s %s' % (old_branch, f.local))



""" ------------------------------- PRIVATE ------------------------------- """


def _valid_new_name(name):
    branches = [ b.branch for b in gitcore.branches(True, False, True) ]
    return name not in branches
