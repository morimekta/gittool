import os, re, argparse

from gittool import diffhelper
from gittool import diffutil
from utils import char
from utils import color
from utils import console
from utils import interactive
from utils import selection

class DiffTool(object):
    """Advanced diffing tool.

    DiffTool          - The difftool.
    DiffTool.File     - A file that is diffed.
    DiffTool.Path     - Path helper (interface).
    DiffTool.Provider - Diff origin provider.
    """

    class File(object):
        UNKNOWN  = '??'
        ADDED    = 'A'
        DELETED  = 'D'
        MODIFIED = 'M'

        """File model.
        
        Model holding information on a file, like it's display name, origin and
        local paths, status and real diff stats.
        """
        def __init__(self, name, origin, local, status):
            self.name = name
            self.origin = origin
            self.local = local
            self.status = status

            # Must be filled from Provider.annotate(...)
            self.differs = False
            self.lines_added = 0
            self.lines_deleted = 0
            self.lines_modified = 0


    class Path(object):
        """Repository path helper interface.

        DiffTool.Path is a helper interface for getting the right repository
        paths for the current repository given a relative paths, and getting
        absolute path for files in the repository given a 'repository path'.
        """
        def get_root(self):
            """Get the absolute path of the repository root. Note that there is
            no guarantee that get_root() + '/' + repository_path(file) is
            pointing to a valid path.

            return {string} Absolute path.
            """
            pass

        def repository_path(self, relative):
            """Get the repository or displayable path.

            The repository path is a relative path from the repository root or
            root/subdir. This can also be called the 'display path', as it's
            used to represent the file in selections and output.

            - relative {str} The relative path from PWD.
            return {str} The relative path from git-root/subdir.
            """
            pass

        def local_path(self, repository):
            """Get the absolute path given a repository path.

            Get the absolute path (entirely from root) given the repository
            path.  The repository path is assumed to be equivalent to path
            gained from repository_path.

            NOTE: local_path(file) must return '/dev/null' when file don't
            exists.

            - repository {str} The repository path.
            return {str} The absolute local path.
            """
            pass

        def display_path(self, repository, relative=False):
            """Get the path to display for a given repository path.

            - repository {str} The repository path.
            - relative {boolean} Display relative path instead of repository
                                 path. [default=True]
            """
            pass

    class Provider(object):
        """Difference (origin) provider.

        The DiffProvider is a provider of files and file lists. Specifically:
        - initialize()      Initialize provider for fetching file info.
        - cleanup()         Clean up temporary state (and files).

        - status()          Get a dict of files to diff status.
        - origin_path(file) Get an origin file path to run diff against.
        - annotate(files)   Annotate diff details on files.
                            This should fill in the differs and lines_* fields.
        """
        def __init__(self):
            """Create a Provider instance."""
            pass

        def initialize(self):
            """Method for doing pre-diff initialization.

            This method will only be run before the diff-provider is going to be
            used.
            """
            pass

        def cleanup(self):
            """Clean up temporary diffing state.
            
            Method for cleaning up internal states or side-effects from doing
            the diff. E.g. temporary files.
            """
            pass

        def status(self):
            """Fetch the list of modified files, as a dict from file path
            (within repository) to diff status.

            return {dict(str: str)} Dict of files.
            """
            pass

        def origin_path(self, repository):
            """Get the absolute path for the origin of a file.

            Get the absolute path (entirely from root) for the file origin,
            given the repository path.  The repository path is assumed to be
            equivalent to path gained from repository_path.

            NOTE: origin_path(file) must return '/dev/null' when file don't
            exists.

            - repository {str} The repository path.
            return {str} The absolute origin path.
            """
            pass

        def annotate(self, fileset):
            """Annotate a list of Diff.File objects with real differences.

            - fileset {list(DiffTool.File)} List of files to annotate.
            return {list(DiffTool.File)} The annotates list.
            """
            return fileset


    def __init__(self, path, parser):
        """Create a DiffTool instance.

        - path {lambda: DiffTool.Path} Function that returns a path helper.
        - parser {ArgumentParser} Argument parser instance to hold program or
                command flags. The flags instance *must* accept arbitrary
                arguments called 'files'.
        """
        self.__path = path

        parser.add_argument('-a', '--all', action='store_true',
                help='Show diff on all modified files (no per-file prompting).')
        if diffhelper.allow_diffall():
            parser.add_argument('-d', '--diffall', action='store_true',
                    help='Show all diffs in a single window.')

        parser.add_argument('-f', '--filter', type=re.compile,
                help='Limit to files matching the filename pattern.')
        parser.add_argument('-e', '--exclude', type=re.compile,
                help='Exclude files matching the filename pattern.')
        parser.add_argument('-m', '--modified', action='store_true',
                help='Limit to files that are modified (both origin and ' +
                'local exist).')
        parser.add_argument('-w', '--write', action='store_true',
                help='Make the local files inline modifiable.')
        parser.add_argument('files', type=str, nargs='*',
                help='Named files to show diff on (relative path).')

    def get_path(self):
        """Get the path helper instance

        return {DiffTool.Path} The path helper.
        """
        return self.__path()

    def run(self, flags, provider):
        """Run the diff using given provider. It is assumed that the flags has
        already been parsed.

        - flags {argparse.Namespace} The parsed arguments.
        - provider {DiffTool.Provider} The diff provider.
        """
        provider.initialize()
        self.__flags = flags

        f = self.__flags.files
        files = dict()
        if len(f) > 0:
            for file in f:
                files[file] = DiffTool.File.UNKNOWN
        else:
            files = provider.status()

        # Handle --filter and --exclude and --modified
        files = self.__filter_files(files)

        fileset = self.__get_fileset(provider, files)
        fileset.sort(lambda x, y: cmp(x.name, y.name))

        self.__show_diff(fileset)

        provider.cleanup()

    def __filter_files(self, files):
        """Filter files based on the --filter and --exclude flags.
        
        - files {list} List of files to filter.
        return {list} List of filtered files.
        """
        tmp = files.keys()
        if self.__flags.filter != None:
            for f in tmp:
                if self.__flags.filter.search(f) == None:
                    del files[f]
        tmp = files.keys()
        if self.__flags.exclude != None:
            for f in tmp:
                if self.__flags.exclude.search(f) != None:
                    del files[f]
        return files

    def __get_fileset(self, provider, files):
        """Get annotated fileset for files.

        - files {list} List of files.
        return {list(DiffTool.File)} Base diff fileset.
        """
        fileset = []
        for file in files:
            origin = provider.origin_path(file)
            local = self.__path().local_path(file)
            fileset.append(DiffTool.File(file, origin, local, files[file]))

        return fileset

    def __show_diff(self, fileset):
        if len(fileset) == 0:
            console.info('No files left to diff.')
            return
        if diffhelper.allow_diffall() and self.__flags.diffall:
            if self.__flags.all:
                for fil in fileset:
                    console.notify('Showing diff on: %s%s%s' %
                        (char.color(color.GREEN), fil.name, char.color(0)))
                if not interactive.confirm(
                        'Show diff on these %d files?' % len(fileset)):
                    return
            else:
                files = map(lambda f: f.name, fileset)
                select = selection.MultiSelect('Select files to diff:', files)
                files = select.select()
                if files == None:
                    return
                if len(files) == 0:
                    console.info('No files left to diff.')
                    return
                files = set(files)

                fileset = filter(lambda f: f.name in files, fileset)
            self.__show_diffall(fileset)
        else:
            for fil in fileset:
                if self.__flags.all:
                    console.notify('Showing diff on: %s%s%s' %
                        (char.color(color.GREEN), fil.name, char.color(0)))
                    # Handles --write
                    self.__show_diffone(fil)
                elif interactive.confirm('Show diff on: %s%s%s?' %
                        (char.color(color.GREEN), fil.name, char.color(0))):
                    # Handles --write
                    self.__show_diffone(fil)
        # DONE

    def __show_diffone(self, fil):
        diffhelper.show_diff(fil.origin, fil.local, self.__flags.write)

    def __show_diffall(self, fileset):
        files = []

        for fil in fileset:
            files.append(fil.origin)
            files.append(fil.local)

        diffhelper.show_diffall(files, self.__flags.write)



class LocalDiffProvider(DiffTool.Provider):
    """Diff-provider for checking arbitrary file locations (e.g. for rsync).
    """
    __re_files_differ = re.compile('^Files .* differ$')

    def __init__(self, path, location):
        super(LocalDiffProvider, self).__init__('local')
        self.__path = path
        location = os.path.normpath(location)
        if location[-1] == '/':
            location = location[:-1]
        self.__try_path(location)
        self.__location = location

    def initialize(self):
        pass

    def cleanup(self):
        self.__path().temp_file_cleanup()

    def status(self):
        origin_files, local_files = self.__get_files(
                self.__location, self.__path().get_root())

        origin_only = origin_files - local_files
        local_only  = local_files  - origin_files

        fileset     = dict()
        for file in origin_only:
            if os.path.isdir(file):
                # Yes, delibeately use an invalid name...
                fileset[file + '/...'] = 'D'
            else:
                fileset[file] = 'D'
        for file in local_only:
            if os.path.isdir(file):
                fileset[file + '/'] = '??'
            else:
                fileset[file] = 'A'

        files_to_diff = local_files - local_only
        for file in files_to_diff:
            if os.path.isdir(self.origin_path(file)):
                fileset[file] = '?A'
            elif os.path.isdir(self.__path().get_root() + '/' + file):
                fileset[file] = 'A?'
            else:
                f = os.popen('diff -q %s %s' % (
                    self.origin_path(file), self.__path().get_root() + '/' + file))
                for line in f:
                    if self.__re_files_differ.search(line) != None:
                        fileset[file] = 'M'
        return fileset

    def origin_path(self, filepath):
        path = self.__location + '/' + filepath
        if not os.path.exists(path):
            return '/dev/null'
        return path

    def annotate(self, fileset):
        for file in fileset:
            if file.status == 'M':
                added, deleted, modified = diffutil.get_diff_stats(
                        self.__location + '/' + file.name,
                        self.__path().root + '/' + file.name)

                file.differs = added > 0 or deleted > 0 or modified > 0
                file.lines_added = added
                file.lines_deleted = deleted
                file.lines_modified = modified
            elif file.status == 'A':
                file.differs = True
                file.lines_added = diffutil.get_file_lines(
                        self.__path().root + '/' + file.name)
            elif file.status == 'D':
                file.differs = True
                file.lines_deleted = diffutil.get_file_lines(
                        self.__location + '/' + file.name)

    def __join(to_check, path):
        if to_check == '':
            return path
        return os.join(to_check, path)

    def __get_files(self, origin_path, local_path):
        origin_files = set()
        local_files = set()

        directories_to_check = set([''])

        while len(directories_to_check) > 0:
            to_check = directories_to_check.pop()
            local = set(os.listdir(local_path + '/' + to_check))
            origin = set(os.listdir(origin_path + '/' + to_check))


            for f in local:
                file = self.__join(to_check, f)
                if f in origin:
                    # In both
                    if (os.path.isdir(local_path + '/' + file) and
                            os.path.isdir(origin_path + '/' + file)):
                        # Dir in both.
                        directories_to_check.add(file)
                    else:
                        # File (or mix file <-> dir)
                        local.add(file)
                        origin.add(file)
                else:
                    # TODO(steineldar): Get files in subfolders...
                    local.add(file)
            for f in origin:
                file = self.__join(to_check, f)
                if not f in local:
                    origin.add(file)

        return origin_files, local_files

    def __try_path(self, location):
        # The location must be long enough to be valid. Shortest possible is:
        #  - ~/ab
        # Thus automatically disallows '/'.
        if not len(location) > 4:
            raise Exception('Invalid location set for local repository. ' +
                            'Too short to be valid: ' + location)
        # The location must exist, and be a directory.
        if not os.path.isdir(location):
            raise Exception('Invalid location set for local repository. ' +
                            'No such directory: ' + location)
        # The Can not be:
        #  * User home, but not a subdirectory of a user directory.
        #     /home/.*/.*
        if (re.search(r'^/home', location) != None and
                re.search(r'^/home/.*/.*$') == None):
            raise Exception('Invalid location set for local repository. ' +
                            'If under /home, must be a user subdirectory: ' +
                            location)
        #  * Under media, but not a subdirectory of a media location.
        #     /media/.*/.*
        if (re.search(r'^/media', location) != None and
                re.search(r'^/media/.*/.*$') == None):
            raise Exception('Invalid location set for local repository. ' +
                            'If under /media, must be a subdir of the ' +
                            'mounted device: ' + location)
        #  * Under /usr, but not a subdirectory of /usr/local [for deployments].
        #     /usr/local/.*
        if (re.search(r'^/usr', location) != None and
                re.search(r'^/usr/local/.*$') == None):
            raise Exception('Invalid location set for local repository. ' +
                            'If under /usr, must be a subdir /usr/local: ' +
                            location)
        #  * Under any other system folder.
        #     /bin /sbin /var /tmp /etc /proc /run /lib* /dev
        if re.search(r'^/(bin|sbin|var|tmp|etc|proc|run|lib|lib32|lib64|dev)',
                location) != None:
            raise Exception('Invalid location set for local repository. ' +
                            'System folder not allowed: ' + location)
