import re
import os

from datetime import datetime
import subprocess
import tempfile

from gittool import diffutil
from gittool import gitconfig
from utils import timeutil

def git_root():
    """Get the absolute path of the git root directory."""
    top = None
    f = subprocess.Popen(['git', 'rev-parse', '--show-toplevel'],
            stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    f.wait()
    for l in f.stderr:
        if len(l.strip()) > 0:
            raise Exception(l.strip())
    for l in f.stdout:
        top = l.strip()
    return top


class Commit(object):
    log_pattern = re.compile('^' +
        '(?P<id>[0-9a-f]*) ' +
        '(?P<user>[^<]*) ' +
        '<(?P<email>[-._@0-9a-zA-Z]*)> ' +
        '(?P<date>.* [-+][0-2][0-9][03]0) ' +
        '(?P<comment>.*)$')
    log_format = '%H %an <%ae> %ci %s'

    def __init__(self, match=None):
        if match != None:
            self.id = match.group('id')
            self.author = match.group('user')
            self.email = match.group('email')
            self.date = match.group('date')
            self.datetime = timeutil.parse_iso(self.date)
            self.message = match.group('comment')

    def __str__(self):
        ids = vars(self)
        out = []
        for id in ids:
            out.append('%s=%s' % (id, repr(ids[id])))
        return 'Commit(%s)' % ','.join(out)


class Branch(object):
    branch_pattern = re.compile('^' +
            '(?P<current>.) ' +
            '(?P<branch>\S*)  *' +
            '(?P<revision>[0-9a-f]*) ' +
            '(?P<message>.*)$')
    __remote_pattern = re.compile('^(remotes/)')
    # parked/ is new gt style, zDone_ is g5 (fleury) style.
    __parked_pattern = re.compile('^(parked/|zDone_)')
    def __init__(self, match):
        if match != None:
            branch = match.group('branch')
            self.current = (match.group('current') == '*')
            self.branch = self.__remote_pattern.sub('', branch)
            self.revision = match.group('revision')
            self.message = match.group('message')
            self.remote = (self.__remote_pattern.search(branch) != None)
            self.parked = (self.__parked_pattern.search(self.branch) != None)
        else:
            self.current = False
            self.branch = None
            self.revision = ''
            self.message = ''
            self.remote = False
            self.parked = False
        self.tracking = None
        self.diffbase = None

        self.ahead_ = None
        self.behind_ = None

    def __str__(self):
        return self.branch


def branches(local=True, remotes=False, parked=False, name=None):
    """Get a list of 'active' branch names.

    - local   {bool} Include local branches [default=True].
    - remotes {bool} Include remote branches [default=False].
    - parked  {bool} Include parked branches [default=False].
    - name    {Regex|str} Matching the given name (must still be within
                     the matched branches above).
    return {list(Branch)} The list of branches.
    """
    global __branches
    if __branches == None:
        __branches = []

        cmd=['git', 'branch', '-v', '--no-abbrev', '-a']
        f = subprocess.Popen(cmd,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        f.wait()
        for line in f.stderr:
            if len(line.strip()) > 0:
                raise Exception(line.strip())
        for line in f.stdout:
            match = Branch.branch_pattern.match(line.rstrip())
            if match != None:
                __branches.append(Branch(match))

    tracked = gitconfig.get_all_tracking()

    res = []
    for b in __branches:
        if name != None and type(name) == str and name != b.branch:
            continue
        elif name != None and type(name) != str and name.match(b.branch) == None:
            continue

        if not b.remote and tracked.has_key(b.branch):
            b.tracking = tracked[b.branch]
        if b.remote:
            # origin/HEAD branch not interesting for us here.
            if remotes and b.branch[-5:] != '/HEAD':
                res.append(b)
        elif b.parked:
            if parked:
                res.append(b)
        else:
            if local:
                res.append(b)
    return res


def get_branch(name):
    """Get the branch matching the name.

    - name {str} Name of the branch.
    """
    bs = branches(local=True, remotes=True, parked=True, name=str(name))
    if len(bs) > 0:
        return bs[0]
    return None


def remote_branches():
    return branches(False, True, False)


def parked_branches():
    return branches(False, False, True)


def current_branch():
    """Get the name of the current branch.

    return {str} The current branch, e.g. 'my-g5-client' or 'zDone_blabla'
    """
    f = os.popen('git branch | grep "^\* " | sed s/^..//')
    ret = ''
    for line in f:
        ret = line.strip()
    return ret


def current_revision(branch=None):
    """Get the current revision hash code. If branch name is given get the
    latest revision for given branch.

    - branch {string} The branch name.
    return {string} Revision code, e.g.
                    '6f859d003c820207b03d8134f1beddb0f91d9a9e'.
    """
    revision = ''
    if branch == None:
        branch = current_branch()
    cmd = []
    cmd.append('git branch -a -v --no-abbrev')
    cmd.append('grep -e \'^..%s \' -e \'^..remotes/%s \'' % (branch, branch))
    cmd.append('sed -e \'s:^..[^ ]* *::\' -e \'s: .*::\'')

    f = os.popen(' | '.join(cmd))
    for line in f:
        revision = line.strip()
    return revision


def common_ancestor(branch=None, local_branch=None):
    """Get the common revision ancestor to given branch, or to the last merge of
    current branch.

    - branch {string} The branch name.
    return {string} The revision code, e.g.
                    '6f859d003c820207b03d8134f1beddb0f91d9a9e'.
    """
    cmd = []
    if branch == None:
        cmd = ['git', 'show-branch', '--merge-base']
    else:
        local = current_revision(local_branch)
        remote = current_revision(branch)
        cmd = ['git', 'merge-base', local, remote]

    f = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    f.wait()

    res = ''
    for l in f.stderr:
        if len(l.strip()) > 0:
            raise Exception(l.strip())
    for l in f.stdout:
        if len(l.strip()) > 0:
            res = l.strip()
    return res


def git_status(revision=None):
    """Status of the git client, as list of files.

    Possible statuses:
    - 'M', 'A', 'D' : Tracked modifications.
    - 'UU'          : Unresolved update.
    - 'AA'          : Both added (unresolved).
    - 'UD'          : Changed remotely, deleted locally.
    - '-M', '-D'    : Untracked modifications.
    - '??'          : Untracked file (not added with git commit -a). Or
                      changed locally but deleted remotely.

    return {dict(file : status)} The status map.
    """
    if revision != None:
        return diffutil.parse_diff_status(
                'git diff ' + revision)
    f = os.popen('git status --porcelain')
    status = dict()
    for line in f:
        match = __git_status_line.match(line)
        if match != None:
            s = match.group('status')
            if __git_untracked_status.search(s) != None:
                s = re.sub(' ', '-', s)
            status[match.group('file').strip()] = s.strip()
    return status


def has_modified_files(include_untracked=False):
    """If the local GIT client has modified files.

    - include_untracked {boolean} Set to true to include if the client has
                                  untracked files.
    return {boolean} True if there are modified files.
    """
    status = git_status()
    if include_untracked:
        return len(status) > 0
    for st in status.values():
        if st != '??':
            return True
    return False


def get_commits(num=10):
    """Get a list of recent commits.

    - num {number} Max number of commits to see.
    return {list(Commit)} List of commits.
    """
    f = os.popen('git log -%d --pretty=format:"%s"' % (num, Commit.log_format))
    commits = []
    for line in f:
        match = Commit.log_pattern.match(line)
        if match == None:
            continue
        commits.append(Commit(match))
    return commits


def revision_info(revision):
    """Get information about a revision.

    - revision {string} The revision ID.
    """
    cmd = [
        'git log --pretty=format:"%s" %s' % (Commit.log_format, revision),
        'grep "^%s"' % revision
        ]
    f = os.popen(' | '.join(cmd))
    commit = None
    for line in f:
        match = Commit.log_pattern.match(line)
        if match == None:
            continue
        commit = Commit(match)
    return commit


def revision_diff(local_revision, remote_revision):
    ahead = 0
    behind = 0

    cmd1 = 'git log %s..%s | grep "^commit"' % (remote_revision, local_revision)
    cmd2 = 'git log %s..%s | grep "^commit"' % (local_revision, remote_revision)

    f = os.popen(cmd1)
    for line in f:
        if len(line.strip()) > 0:
            ahead += 1
    f = os.popen(cmd2)
    for line in f:
        if len(line.strip()) > 0:
            behind += 1

    return ahead, behind

def tag_for_revision(revision):
    f = subprocess.Popen(['git', 'describe', '--exact-match', revision],
            stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    f.wait()
    tag = None
    for line in f.stdout:
        if len(line.strip()) > 0:
            tag = line.strip()
    for line in f.stderr:
        if len(line.strip()) > 0:
            tag = None
    return tag


def file_copy(revision, path, out):
    """Read a file from given revision and copy to target file.

    - revision {string} The revision ID to read the frile from. Use
                        current_revision() to fetch this.
    - in       {string} The file path to read.
    - out      {string} The file to write output to.
    """
    cmd = ['git', 'cat-file', '-p', '%s:%s' % (revision, path)]
    outfile = None
    popen = subprocess.Popen(cmd,
            stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        outdata, errdata = popen.communicate()
        for l in errdata.split('\n'):
            l = l.strip()
            if len(l) > 0:
                raise Exception(l)

        outfile = open(out, 'w')
        outfile.write(outdata)
        outfile.flush()
    except KeyboardInterrupt as ke:
        if outfile != None:
            outfile.close()
    finally:
        if outfile != None:
            outfile.close()


""" ------------------------------- PRIVATE ------------------------------- """


__branches = None

__git_status_line = re.compile(r'^(?P<status>..) (?P<file>.*)')
__git_untracked_status = re.compile('[ ?]\S')

__re_remote_branch = re.compile('remotes/')

"""
TODO(steineldar): Create and manage remote repository.

## Setting up a remote repository:
 git remote add trekstor /media/TrekStor/src/gittool.git/
 git config remote.trekstor.fetch +refs/heads/master:refs/remotes/trekstor/master
 git fetch trekstor

## Setting the remote origin for a branch (branch.master = local branch "master").
 git config branch.master.remote trekstor
 git config branch.master.merge refs/heads/master


###  ----------------------- ###

git remote add dongle /media/817E-13DF/workspace
git config remote.dongle.fetch +refs/heads/"master":refs/remotes/"dongle/master"
git for-each-ref --shell --format='git branch -dr %(refname:short)' refs/remotes/dongle
git fetch dongle

git diff dongle/master | ...

git remote add origin /tmp/myrepo.git
git config branch.master.remote origin
git config branch.master.merge refs/heads/master

Will return blank if branchName is not valid. Otherwise: "refs/heads/$normalized"
"""
