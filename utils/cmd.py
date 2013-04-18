"""Utils for making secondary-command programs.

This util wraps around the concept of a "secondary command" program, meaning
that the program takes in a secondary "command" or "action". E.g.:

  gittool diff -ad

Runs program gittool, with the "action" diff, and arguments for diff "-ad".
"""

import os
from utils import interactive
from utils import console


class Command(object):
    """Mini command wrapper.

    Holds a single command (but not implemented).
    """
    def __init__(self, command, short):
        self.command = command
        self.__short = short

    def run(self, argv):
        """Run the command with arguments."""
        raise Exception('Command not implemented: ' + self.command)

    def help(self):
        """Print the help page for the command.

        This help page should explain the entire command and it's arguments."""
        print 'g5 %s : %s' % (self.command, self.__short)

    def help_short(self):
        """Return short help-string.

        This string is used in the g5 command overview (g5 help) or when the
        illegal commands are tried.
        """
        return self.__short


class Program(object):
    """Helper program containing and handling the Commands."""

    def __init__(self, program):
        """Crestes a Program instance.

        - program {string} The program name (prefferably taken from the
                           command line (argv[0]).
        """
        path, self.program = os.path.split(program)
        paths = os.environ['PATH'].split(':') + ['.']
        if not path in paths:
            self.program = program
        self.commands = dict()
        self.short = dict()

    def add(self, cmd, short=None):
        """Add a command to the program.

        - cmd {Command} The command instance to add.
        - short {string=None} (Optional) Short name for the command.
        """
        self.commands[cmd.command] = cmd
        if short != None:
            self.short[short] = cmd

    def run(self, command, argv):
        """Run command with arguments.

        - command {string} The command name.
        - argv {list(str)} The arguments sent to the command.
        """
        if command == 'help':
            if len(argv) > 0:
                help = argv[0]
                cmd = self.commands.get(help)
                if cmd is not None:
                    cmd.help()
                    return

                raise Exception('No such command: ' + command)
            self.help()
            return

        cmd = self.__get_cmd(command)
        return cmd.run(argv)

    def help(self, command=None):
        """Print help message.

        If no command is given shows the program help with an overview
        over the available commands.

        - command {string=None} (Optional) If given, show help for this
                             command and not for the program as a whole.
        """
        if command != None:
            try:
                cmd = self.__get_cmd(command)
                cmd.help()
            except Exception as ex:
                console.warn(str(ex))
                pass
            else:
                return
        self.print_usage()
        print ''
        print 'Commands available are:'

        longest = 10

        commands = self.commands.keys()
        commands.sort()
        for cmd in commands:
            desc = self.commands[cmd].help_short()
            print '  %s - %s' % (cmd.ljust(longest), desc)

        print ''
        print 'Short hands for some well used commands:'
        commands = self.short.keys()
        commands.sort()
        for cmd in commands:
            full = self.short[cmd].command
            print '  %s - %s %s - %s' % (
                    full.ljust(longest), self.program, cmd.ljust(3),
                    self.short[cmd].help_short())

        print ''

    def __get_cmd(self, command):
        cmd = self.commands.get(command)
        if cmd is not None:
            return cmd

        cmd = self.short.get(command)
        if cmd is not None:
            return cmd

        raise Exception('No such command: ' + command)

    def print_usage(self):
        print '%s is a thin wrapper around git for my personal workflow.' % self.program
        print 'usage: %s <command> [<flags>*] [--] [<file>*]' % self.program
        print '       %s help [command]' % self.program
