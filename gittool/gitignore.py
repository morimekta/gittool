import os, re, fnmatch


def gitignore_files(path):
    """Return a list of the gitignore files that affects the file at 'path' or
    files in 'path'. Path may be a relative path.

    - path {string} The file or directory path.
    """
    path = os.path.abspath(os.path.normpath(path))
    if path[-1] == '/':
        path = path[:-1]

    files = set()
    while len(path) > 1:
        if os.path.exists(path + '/.gitignore'):
            files.add(path + '/.gitignore')
        if os.path.exists(path + '/.git'):
            if os.path.exists(path + '/.git/info/exclude'):
                files.add(path + '/.git/info/exclude')
            break
        path, out = os.path.split(path)
    return files


def gitignore_globs(path):
    """Return a list of the glob patterns that affects 'path' or files in
    'path'. Path may be a relative path.

    - path {string} The file or directory path.
    """
    global __globs
    if __globs == None:
        __globs = []
        for gitignore in gitignore_files(path):
            file = open(gitignore, 'r')
            try:
                for line in file:
                    line = line.strip()
                    line = re.sub('^#.*', '', line)
                    if line == '':
                        continue
                    __globs.append(line)
            finally:
                file.close()
    return __globs


def ignore_file(path):
    """Check if the file path given should be ignored (by gittool)."""
    globs = gitignore_globs(path)

    p, name = os.path.split(path)
    if os.path.isdir(path):
        name += '/'

    for glob in globs:
        if glob[0] == '!':
            if fnmatch.fnmatch(name, glob):
                return False
        elif fnmatch.fnmatch(name, glob):
            return True
    return False


""" ------------------------------ PRIVATE --------------------------------- """


__globs = None
