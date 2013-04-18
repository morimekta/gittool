import os, re

from gittool import gitcore
from gittool import gitconfig

def branch_arg(branch, local=True, remote=False, parked=False):
    """Get branch from branch name in argument.

    - branch {string} The branch name to check.
    return {string} The branch name. Or raises an exception if not a branch.
    """
    br = map(lambda b: b.branch, gitcore.branches(local, remote, parked))
    if branch in br:
        return branch
    raise Exception('%s is not a branch, branches = [%s]' %
            (branch, ', '.join(br)))


def repository_location_arg(path):
    """Returns a normalized absolute path if the location is a git repository,
    raises an exception otherwise.

    - path {str} The repository location.
    return {str} The repository location, or raises an exception if no such
                 repository.
    """
    if not os.path.isdir(path):
        raise Exception('No such directory: ' + path)
    return os.path.normpath(os.path.abspath(path))


def verify_branch_character(char):
    """Verifies that a character is valid input for a branch name.

    - char {char} The char key (from getch()) to verify.
    return {string?} Error string message to show or None if no problem.
    """
    if (len(char) == 0 or ord(char[0]) < 40 or char in __illegal_chars):
        return 'Character ' + repr(char) + ' not allowed in branch name.'
    return None


def verify_branch_name(branch, origin=None):
    """Verify that a string is valid result for a new branch name.

    - branch {string} The branch name to verify.
    return {string?} Error string message to show or None if no problem.
    """
    check = branch
    for c in check:
        ret = verify_branch_character(c)
        if ret != None:
            return ret
    if origin != None:
        check = '%s/%s' % (origin, branch)
    br = map(lambda b: b.branch, gitcore.branches())
    if check in br:
        if origin != None:
            return 'Repository %s already has branch %s.' % (origin, branch)
        return 'Branch name ' + branch + ' already in use.'
    m = __re_illegal_sequence.match(branch)
    if m != None:
        return ('Branch name containing illegal character sequence ' +
                repr(m.group(1)))
    m = __re_illegal_postfix.match(check)
    if m != None:
        return 'Branch name cannot end with ' + repr(m.group(1))
    # Except for the leading '.' check, this is self imposed name checking to
    # avoid branch name vs. ref-name confusion. The parked/ prefix is reserved
    # for 'parked' branches.
    m = __re_illegal_prefix.match(check)
    if m != None:
        return 'Illegal branch prefix: ' + repr(m.group(1))
    return None


def verify_remote_branch_name(repository):
    return lambda branch:verify_branch_name(branch, origin=repository)


def get_verified_diffbase(branch, allow_tracking=True, allow_master=True):
    # First make sure that the diffbase is valid.
    diffbase = gitconfig.get_diffbase(branch, False, False)
    if diffbase is not None:
        diff_branch = gitcore.get_branch(diffbase)
        if diff_branch is None:
            gitconfig.set_diffbase(branch, None)
    return gitconfig.get_diffbase(branch, allow_tracking, allow_master)


""" ------------------------------- PRIVATE ------------------------------- """


__illegal_chars = ['\x7f', ':', '"', '\'', '~', '\\', '?', '^', '*', '[']
__re_illegal_sequence = re.compile('.*(/\.|@{|\.\.)')
__re_illegal_postfix = re.compile('.*(/|\.|\.lock)$')
__re_illegal_prefix = re.compile('^(\.|heads/|tags/|remotes/|parked/|zDone_)')
