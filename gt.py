#!/usr/bin/python
import sys

from gittool import branches
from gittool import gitcmd
from utils import cmd
from utils import console


def main(argv):
    run_gt(argv[0], argv[1:])


def run_gt(program, argv):
    command = None
    params = []
    if len(argv) > 0:
        command = argv[0]

    if len(argv) > 1:
        params = argv[1:]

    gt = cmd.Program('gt')

    # Add GIT commands, or slightly modified GIT commands.
    # TODO(steineledar): Fix, these are currently broken or incomplete:
    # gt.add(gitcmd.GitWrapper('checkout')) # Needs interactive selection.
    # Augmented git with updated interactiveness.
    #   e.g. git add -i, just using the good tools.
    gt.add(gitcmd.Diff())
    gt.add(gitcmd.Status(), 'st')

    # My own GIT commands
    # - diff should get diff-stats in the selection (flag).
    #        and should have --merge for seeing diff on conflicts
    #        only.
    gt.add(branches.Branch(), 'b')
    gt.add(branches.NewCL())

    # TODO(steineldar): Add commands:
    # - remote
    #    -- list current remote branches.
    #        * f = fetch
    #        * d = delete
    #        * n = new (set up)
    # - commit (really only a short-hand, but with updated --interactive add).
    #        commit to master block.

    if command is not None:
        try:
            gt.run(command, params)
        except Exception as ex:
            console.error(str(ex))
            print ''
            gt.help()
            sys.exit(1)
    else:
        gt.help()

if __name__ == '__main__':
    main(sys.argv)
