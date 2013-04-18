import threading
import sys, re

# g5
from utils import char
from utils import color
from utils import console
from utils import getch
from utils import strutil


class Select(object):
    """Base class for Select dialogs.

    s = select.select()

    Is either some some selection, or can be None if no items were selected.

    Must be implemented:
    Make a line, the part after ' dd '
    s.make_line(item, colorset)
    s.handle_select(item)
    """
    class Cmd(object):
        """Command holder for Select."""
        # Return value for functions that want to exit the selection with None
        # value. The escape endings are to ensure this is a *very* unlikely
        # normal return value.
        EXIT = char.ESC + '---exit---' + char.ESC
        # Return value for functions that want to stay in the selection. The
        # escape endings are to ensure this is a *very* unlikely normal return
        # value.
        STAY = char.ESC + '---stay---' + char.ESC

        def __init__(self, char, name, function):
            """Make a Select.Cmd instance.

            - char {char} The command key (or character).
            - name {string} Name of the command for displaying.
            - function {lambda item: *} The function handling the command.
            """
            self.char = char
            self.name = name
            self.function = function

        def __str__(self):
            return self.char + '=' + self.name

        def __call__(self, item):
            return self.function(item)


    __colorset = color.ColorSet(color.DEFAULT)
    __highlight = __colorset.mod(color.BG_BLUE)

    def __init__(self, prompt, items,
                commands  = [],
                current   = 0,
                page_size = 20,
                linewidth = None,
                mkline = lambda item, colorset: str(item),
                margin=5):
        """
        Make a selection instance.

        - prompt   {str}        Prompt string.
        - items    {list(*)}    List of items to chooce from.
        - commands {list(Select.Cmd)}
                                Commands list, char=name to lambda item: item
                                dictionary. If the function returns None, then
                                nothing is done. Else return the result of the
                                function as return value.
        - page_size {int}       Number of items to display on a page.
        - margin    {int}       How many items more than page_size before paging
                                is triggered.
        - mkline {lambda x:str} Lambda function to make the line content to be
                                displayed.
        - linewidth {int}       Max width for each line. [default=term-size]
        TODO(steineldar): Make linewidth None, and default to terminal width.
             - Also trim lines to max width.
        """
        self.lines = []
        self.__printed_lines = 0

        self.__current  = 0
        self.__items    = items
        self.__mkline   = mkline
        self.__commands = dict()
        # Limit the line width to this many displayed characters. Also make sure
        # lines are cleared up to this when re-printed.
        if linewidth == None:
            rows, linewidth = console.terminal_size()
        self.__width = linewidth

        cmds = []
        commands.append(Select.Cmd('q', 'quit', lambda item: Select.Cmd.EXIT))
        for cmd in commands:
            cmds.append(str(cmd))
            self.__commands[cmd.char] = cmd
        cmds.sort()

        # The initial line, telling what the selection is about.
        self.__prompt = '%s [%s]' % (prompt, ', '.join(cmds))

        # Number entered (so far) for quick focus.
        self.__num = ''

        self.__page_size = page_size
        self.__start = 0
        self.__show_items = len(self.__items)
        self.__paged = False
        if self.__show_items > (page_size + margin):
            self.__show_items = self.__page_size
            self.__paged = True

        if current > 0:
            self.__update_page(current)

    def __write_lines(self):
        # Trace back.
        sys.stdout.write('\r')
        if self.__printed_lines > 1:
            sys.stdout.write(char.cursor_up(self.__printed_lines - 1) +
                char.cursor_erase())

        sys.stdout.write(('\n\r' + char.cursor_erase()).join(self.lines))
        sys.stdout.flush()
        self.__printed_lines = len(self.lines)

    def update(self):
        self.__update_lines()
        self.__write_lines()

    def num_items(self):
        return len(self.__items)

    def item(self, idx):
        return self.__items[idx]

    def make_line(self, item, colorset):
        return self.__mkline(item, colorset)

    def handle_select(self, select):
        return select

    def select(self):
        return console.replace_notify(
                lambda: getch.interactive(lambda getkey: self.__select(getkey)))

    def __complete(self):
        sys.stdout.write('\n\r')
        sys.stdout.flush()

    def __update_page(self, new_current):
        self.__current = max(0, min(new_current, len(self.__items) - 1))
        if self.__paged:
            self.__start = int(
                    self.__current / self.__page_size) * self.__page_size

    def __handle_navigation(self, ch):
        self.__num = ''

        if ch == char.UP:
            self.__update_page(self.__current - 1)
            self.update()
            return True

        if ch == char.DOWN:
            self.__update_page(self.__current + 1)
            self.update()
            return True

        if ch in [char.PAGE_UP, char.LEFT]:
            self.__update_page(self.__current - self.__page_size)
            self.update()
            return True

        if ch in [char.PAGE_DOWN, char.RIGHT]:
            self.__update_page(self.__current + self.__page_size)
            self.update()
            return True
        return False

    def __handle_numeric(self, ch):
        if ch in '0123456789':
            self.__num = self.__num + ch
            value = int(self.__num)
            # We have over-stepped. Restart the numbering.
            if value > self.num_items():
                self.__num = ch
                value = int(self.__num)
            # We have reached max number, that is 10*x is greater than.
            if 10 * value > self.num_items():
                self.__num = ''

            if value > 0:
                self.__update_page(value - 1)
            self.update()
            return True
        self.__num = ''
        return False

    def __select(self, getkey):
        sys.stdout.write(self.__prompt + '\n\r')
        self.update()
        while True:
            ch = getkey()
            if ch in [char.ESC, char.EOF]:
                self.__complete()
                raise Exception('User interrupted.')

            if ch in ['q', char.ABORT]:
                self.__complete()
                return None

            elif self.__handle_numeric(ch):
                continue

            elif self.__handle_navigation(ch):
                continue

            if ch in self.__commands.keys():
                console.reset_linecounter()
                ret = self.__commands[ch](self.__items[self.__current])
                if ret == Select.Cmd.EXIT:
                    self.__complete()
                    return None
                elif ret != Select.Cmd.STAY:
                    self.__complete()
                    return ret
                back = console.reset_linecounter()
                if back > 0:
                    sys.stdout.write('\r')
                    for i in range(back):
                        sys.stdout.write(char.cursor_erase() + char.cursor_up(1))
                # Both selection and content may have changed.
                self.update()
                continue

            if ch in ['\n', ' ']:
                ret = self.handle_select(self.__items[self.__current])
                if ret != None:
                    self.__complete()
                    return ret

    def __make_hidden_line(self):
        if not self.__paged:
            return None
        hidden_before = self.__start
        hidden_after = max(0, len(self.__items) - self.__start - self.__page_size)

        before = ''.ljust(38)
        after  = ''.rjust(38)

        if hidden_before > 0:
            before = ('<-- (%d items)' % hidden_before).ljust(38)
        if hidden_after > 0:
            after = ('(%d items) -->' % hidden_after).rjust(38)
        return strutil.hilite('  %s%s  ' % (before, after), self.__colorset.yellow)

    def __update_lines(self):
        self.lines = []

        cmdchars = self.__commands.keys()
        cmdchars.sort()

        hidden_line = self.__make_hidden_line()
        if hidden_line != None:
            self.lines.append(hidden_line)

        digits = len(str(1 + len(self.__items)))
        for i in range(self.__start, self.__start + self.__show_items):
            if i >= len(self.__items):
                self.lines.append(''.ljust(80))
                continue
            linecolor = self.__colorset
            if self.__current == i:
                linecolor = self.__highlight
            item = self.item(i)
            line = self.make_line(item, linecolor)
            num  = str(1 + i).rjust(digits, '0')
            self.lines.append(strutil.hilite(strutil.trimwidth(
                    ' %s %s' % (num, line), self.__width, True), linecolor))

        if hidden_line != None:
            self.lines.append(hidden_line)

        self.lines.append('Your choice (1..%d or %s): %s' % (
            len(self.__items), ','.join(cmdchars), self.__num.ljust(digits)))



class MultiSelect(Select):
    """Select from a set of pre-defined options and choices.

    E.g. select_multi_choice('Bla', ['A', 'B', 'C'])

    - prompt  {str}       Prompt string.
    - options {list(str)} List of options to chooce from.

    s.select() => List if selected items.
    """
    class Option(object):
        def __init__(self, item):
            self.item = item
            self.selected = False

    def __init__(self, prompt, items,
            mkline=lambda item, colorset: str(item)):
        options = []
        for item in items:
            options.append(MultiSelect.Option(item))

        super(MultiSelect, self).__init__(prompt, options,
                mkline = mkline,
                commands = [
                    Select.Cmd('a', 'all',    self.__all),
                    Select.Cmd('n', 'none',   self.__none),
                    Select.Cmd('i', 'invert', self.__invert),
                    Select.Cmd('x', 'done',   self.__done),
                ])


    def make_line(self, item, colorset):
        """Make selectable line."""
        line = super(MultiSelect, self).make_line(item.item, colorset)

        sel = ' '
        sel_col = colorset.red
        if item.selected:
            sel = 'x'
            sel_col = colorset.green
        return strutil.hilite('[%s] %s' % (sel, line), sel_col)

    def handle_select(self, item):
        item.selected = not item.selected
        self.update()
        return None

    def __all(self, item):
        for i in range(self.num_items()):
            self.item(i).selected = True
        self.update()
        return Select.Cmd.STAY

    def __none(self, item):
        for i in range(self.num_items()):
            self.item(i).selected = False
        self.update()
        return Select.Cmd.STAY

    def __invert(self, item):
        for i in range(self.num_items()):
            self.item(i).selected = not self.item(i).selected
        self.update()
        return Select.Cmd.STAY

    def __done(self, item):
        ret = []
        for i in range(self.num_items()):
            item = self.item(i)
            if item.selected:
                ret.append(item.item)
        return ret
