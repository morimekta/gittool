import os, sys

from gittool import gitignore

def main(argv):
    globs = gitignore.gitignore_globs('.')
    if len(globs) > 0:
        os.system('tree -I "%s" %s' % ('|'.join(globs),  ' '.join(argv[1:])))
    else:
        os.system('tree %s' % ' '.join(argv[1:]))

if __name__ == '__main__':
    main(sys.argv)
