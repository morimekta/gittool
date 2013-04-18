import os, re, hashlib
from datetime import datetime

from gittool import difftool
from gittool import gitcore
from utils import char
from utils import strutil

class GitPath(difftool.DiffTool.Path):
    def __init__(self, progdir, subdir=None):
        """Git path helper.

        Make a path-helper that works with the git paths and the files in the
        git repository. If a subdirectory is provided, it will be stripped from
        the display path (repository_path).

        - progdir {str}      Program directory to store tmp files etc.
        - subdir  {str=None} Subdirectory in which the repository files live.
        """
        self.progdir = progdir
        self.subdir = subdir

        self.pwd = ''
        self.root = ''
        try:
            self.pwd = os.getcwd()
        except OSError as ex:
            raise Exception('Current directory don\'t exist.', ex)

        self.root = gitcore.git_root()
        self.__re_strip_root = re.compile('^' + self.root + '/')

        self.__has_subdir = False
        if self.subdir != None:
            self.__has_subdir = True
            self.__re_subdir_file = re.compile('.*%s/' % subdir)
            self.__re_has_subdir = re.compile('%s/' % subdir)

        self.__temp_files = []
        self.__next_temp_id = 0

        h = hashlib.md5()
        h.update(str(datetime.now()))
        self.__temp_hash = strutil.str2hex(h.digest())


    def get_root(self):
        """ @override

        This is in most cases the git root, but if the subdir param was set
        in the constructor, this is appended to the path.
        """
        if self.__has_subdir:
            return self.root + '/' + self.subdir
        return self.root

    def repository_path(self, relative):
        """ @Override """
        if self.__has_subdir:
            if self.__re_has_subdir.search(relative) != None:
                return self.__re_subdir_file.sub('', relative)
            return self.__re_subdir_file.sub(
                    '', os.path.normpath(os.path.join(self.pwd, relative)))
        return self.__re_strip_root.sub(
                '', os.path.normpath(os.path.join(self.pwd, relative)))

    def display_path(self, repository, relative=False):
        """ @Override """
        path = repository
        if self.__has_subdir:
            path = self.__re_subdir_file.sub('', path)
        if relative:
            return os.path.relpath(os.path.join(self.get_root(), path))
        return path

    def local_path(self, repository):
        """ @Override """
        path = self.root + '/' + repository
        if (self.__has_subdir and
                self.__re_subdir_file.search(repository) == None):
            path = self.root + '/' + self.subdir + '/' + repository
        if not os.path.exists(path):
            return '/dev/null'
        return path

    def program_file(self, name):
        """Get a program specific file path.

        Get the absolute path for a program specific file with given name.

        - name {str} The file name.
        return {str} The program's file absolute path.
        """
        os.system('mkdir -p ' + self.root + '/' + self.progdir)
        return self.root + '/' + self.progdir +    '/' + name

    def temp_file_name(self, suffix):
        """Get a temporary file path.

        Note that this method is not meant to be secure.

        - suffix {str} The temp file name suffix.
        return {str} The absolute path of the temporary file.
        """
        path = self.root + '/' + self.progdir + '/tmp'
        file_name = '.%s.%03d.%s' % (
                self.__temp_hash[4:11], self.__next_temp_id, suffix)
        self.__next_temp_id += 1
        self.__temp_files.append(file_name)

        # Make sure tmp directory exists.
        os.system('mkdir -p ' + path)
        # Create file and make sure it's empty.
        os.system('echo "" > ' + path + '/' + file_name)
        return path + '/' + file_name

    def temp_file_cleanup(self):
        """Clean up temp files.

        Removes handled temp files from the tmp directory. Note that it is safe
        to have multiple handlers, as they should not have colliding temp
        names.
        """
        path = self.root + '/' + self.progdir + '/tmp'
        for temp_file in self.__temp_files:
            os.remove(path + '/' + temp_file)
        self.__temp_files = []


class GitProvider(difftool.DiffTool.Provider):
    """Difference provider for GIT."""
    def __init__(self, path, revision=None, include_untracked=False):
        """Create a GitProvider instance.

        - path {GitPath} GitPath for path canculatons.
        - revision {str=None}       Revision ID to diff against. The revision ID
                                    can be fetched with
                                    gitcore.current_revision() etc.
        """
        super(GitProvider, self).__init__()
        self.__path = path
        self.__revision = revision
        # To enable special check of local GIT diff (untracked files) we need to
        # remember if we didn't get a revision for the git_status call.
        self.__origin = revision
        if revision == None:
            self.__revision = gitcore.current_revision()
        self.__include_untracked = include_untracked

    def initialize(self):
        """ @Override """
        pass

    def cleanup(self):
        """ @Override """
        self.__path().temp_file_cleanup()

    def status(self):
        """ @Override """
        stat = gitcore.git_status(self.__origin)
        if not self.__include_untracked:
            tmp = dict()
            for file in stat:
                if stat[file] != '??':
                    tmp[file] = stat[file]
            stat = tmp
        return stat

    def origin_path(self, filepath):
        """ @Override """
        tmpfile = self.__path().temp_file_name(self.__temp_name(filepath))
        try:
            gitcore.file_copy(self.__revision, filepath, tmpfile)
        except Exception as ex:
            tmpfile = '/dev/null'
        return tmpfile

    def annotate(self, fileset):
        """ @Override """
        f = os.popen('git diff --numstat %s -- %s' % (
            self.__revision, ' '.join(map(lambda f: f.name, fileset))))
        fs = dict()
        for line in f:
            m = __re_diffstat.match(line)
            if m != None:
                fs[m.group('file')] = (int(m.group('add')), int(m.group('del')))
        for fil in fileset:
            f = fs.get(fil.name)
            if f != None:
                _add, _del = f
                fil.has_diff = _add > 0 or _del > 0
                fil.lines_added = _add
                fil.lines_deleted = _del
            else:
                # All is added or removed depending on state [A/D]
                pass

    def __temp_name(self, filepath):
        """Returns a short representation of the files path and name.

        E.g. home/steineldar/g5.py becomes h_s_g5.py .

        - filepath {str} File path to shorten.
        return {str} The shortened temp filename.
        """
        parts = filepath.split('/')
        file = parts[-1]
        short_parts = []
        for part in parts[:-1]:
            short_parts.append(part[0])
        short_parts.append(file)
        return '_'.join(short_parts)

""" ------------------------------  PRIVATE  ------------------------------ """

__re_diffstat = re.compile('(?P<add>[\d][\d]*)  *(?P<del>[\d][\d]*)  *(?P<file>.*)')
