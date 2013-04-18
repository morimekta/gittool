import sys, re

from utils import char
from utils import color
from utils import console
from utils import getch
from utils import strutil
from utils import timer


def confirm(message):
    """Get user confirmation.

    E.g. confirm('Do you o'Really?')

    - message  {str}  Message / question to ask.
    return     {bool} True iff the user confirmed, False otherwise.
    """
    if console.alt_mode():
        sys.stdout.write('\n')
    sys.stdout.write('\r%s [Y/n]: ' % message)
    sys.stdout.flush()
    console.increment_linecounter()

    __last_warning = 0
    while True:
        c = getch.getch()
        if c in ['Y', 'y', '\n']:
            if not console.alt_mode():
                sys.stdout.write('\n\r')
                sys.stdout.flush()
            return True
        elif c in ['N', 'n', char.BACKSPACE]:
            if not console.alt_mode():
                sys.stdout.write('\n\r')
                sys.stdout.flush()
            return False
        elif c in [char.ESC, char.ABORT, char.EOF]:
            if not console.alt_mode():
                sys.stdout.write('\n\r')
            raise Exception('User interruption')
        elif c in [' ', '\t'] or char.is_escape(c):
            continue
        sys.stdout.write(
                strutil.ljust('\r%s [Y/n]: %s is not valid input.' %
                    (message, repr(c)), __last_warning))
        __last_warning = len(repr(c)) + 20
        sys.stdout.flush()

    return False


def getline(message,
        char_verifier   = lambda c: None,
        result_verifier = lambda r: None,
        tab_expander    = None,
        initial_line    = ''):
    """Get user input.

    E.g. i = getline('What is your cow?')

    - message         {str}  Message / question to ask.
    - char_verifier   {lambda x: str} Character verifier. If provided will be
            called for every key / char input and if retuning a string value
            will print that string as 'warning' and ignore the input.
    - result_verifier {lambda x: str} Result verifier. If provided will be
            called when the user presses enter. If the lambda returns a string
            the result will not return but print the warning and wait for more
            user input.
    - tab_expander    {lambda x: (str, str|list(str))} Tab string expander. If
            provided will be called when the user presses the 'tab' button
            ('\\t'), and should return a 2-tuple: first the tab result, or the
            original string if no expansion happened. And second the suggested
            expansion values (as list) or an output message. If tab-expander is
            not provided then the tab key will not work and print a warning.

    return {string|None} Result string or none if empty string entered.
    """
    if tab_expander == None:
        tab_expander = no_tab()
    return LineInput(message,
            char_verifier, result_verifier, tab_expander).readline(initial_line)


def no_tab():
    return lambda s: (s, 'Tab expansion is not supported.')

def ignore_tab():
    return lambda s: (s, None)

def tab_is_space():
    return lambda s: (s + ' ', None)

def expand_tab(ts = 4):
    return lambda s: (s.ljust(len(s) + ts - (len(s) % ts)), None)


""" ----------------------------- PROTECTED -------------------------------- """

class LineInput(object):
    """Reusable terminal line reader.
    """
    __re_space = re.compile('([-.,:;\'\"~^#%&/()\[\]{}?\s]+)')
    __tab_not_supported = 'Tab character <tab> is not supported.'

    def __init__(self, message,
            char_verifier,
            result_verifier,
            tab_expander):
        self.__message = message
        self.__message_len = strutil.display_width(message) + 1
        self.__char_verifier = char_verifier
        self.__result_verifier = result_verifier
        self.__tab_expander = tab_expander

    def readline(self, initial_line):
        initial = self.__result_verifier(initial_line)
        if len(initial_line) > 0 and initial != None:
            raise Exception('Cannot readline with not accepted default: ' + initial)

        self.__before = initial_line
        self.__after = ''
        self.__printed_warning = 0
        self.__text_len = len(initial_line)
        self.__last_char = ''

        return getch.interactive(lambda getkey: self.__real_input(getkey), None)

    def __real_input(self, getkey):
        if console.alt_mode():
            sys.stdout.write('\n')
        while True:
            go_back = 0
            sys.stdout.write('\r%s %s' % (self.__message, self.__before))
            if len(self.__after) > 0:
                sys.stdout.write(self.__after)
                go_back += len(self.__after)

            new_len = len(self.__before + self.__after)
            tmp = self.__text_len - new_len
            if tmp > 0:
                sys.stdout.write(' '.ljust(tmp))
                go_back += tmp
            if go_back > 0:
                sys.stdout.write(char.cursor_left(go_back))
            sys.stdout.flush()
            self.__text_len = new_len

            c = getkey()

            if c in [char.ESC, char.ABORT, char.EOF]:
                if not console.alt_mode():
                    sys.stdout.write('\n\r')
                raise Exception('User interruption')

            elif self.__handle_delete(c):
                pass
            elif self.__handle_direction(c):
                pass
            elif self.__handle_tab(c):
                pass
            elif c == '\n':
                result = self.__before + self.__after
                warning = self.__result_verifier(result)
                if result == '' or warning == None:
                    console.increment_linecounter()
                    if not console.alt_mode():
                        sys.stdout.write('\n\r')
                        sys.stdout.flush()
                    if result == '':
                        return None
                    return result
                self.__print_warning(warning)
            else:
                self.__handle_char(c)
            self.__last_char = c
        raise Exception('Unexpected break in input loop.')


    def __handle_delete(self, c):
        if c == char.DELETE:
            if len(self.__after) > 0:
                self.__after = self.__after[1:]
            return True
        if c == char.BACKSPACE:
            if len(self.__before) > 0:
                self.__before = self.__before[:-1]
            return True
        return False

    def __handle_direction(self, c):
        if len(self.__before) > 0 and c == char.LEFT:
            self.__after = self.__before[-1] + self.__after
            self.__before = self.__before[:-1]

        elif len(self.__after) > 0 and c == char.RIGHT:
            self.__before += self.__after[0]
            self.__after = self.__after[1:]

        elif len(self.__before) > 0 and c == char.CTRL_LEFT:
            before = __re_space.split(self.__before)
            if len(before) < 3:
                self.__after = self.__before + self.__after
                self.__before = ''
            elif __re_space.search(before[-1]) != None:
                self.__after += before[-1] + self.__after
                self.__before = ''.join(before[:-1])
            else:
                self.__after = ''.join(before[-2:]) + self.__after
                self.__before = ''.join(before[:-2])

        elif len(self.__after) > 0 and c == char.CTRL_RIGHT:
            after = __re_space.split(self.__after)
            if len(after) < 3:
                self.__before += self.__after
                self.__after = ''
            elif __re_space.search(after[0]) != None:
                self.__before += after[0]
                self.__after = ''.join(after[1:])
            else:
                self.__before += ''.join(after[:2])
                self.__after = ''.join(after[2:])

        elif c == char.HOME:
            self.__after = self.__before + self.__after
            self.__before = ''

        elif c == char.END:
            self.__before = self.__before + self.__after
            self.__after = ''

        return c in [
                char.LEFT, char.RIGHT, char.CTRL_LEFT, char.CTRL_RIGHT,
                char.HOME, char.END]

    def __handle_tab(self, c):
        if c == '\t':
            self.__before, suggestions = self.__tab_expander(self.__before)
            if type(suggestions) == list:
                if c == self.__last_char:
                    # TODO(steineldar): Format and display suggestions. Maybe
                    # *bellow* the input line?? Or replace the warning line with
                    # a line with the next
                    pass
            elif type(suggestions) == str:
                # The suggestion result is a single string message.
                self.__print_warning(suggestions)
            return True
        return False

    def __handle_char(self, c):
        if char.is_escape(c):
            return
        warning = self.__char_verifier(c)
        if warning != None:
            self.__print_warning(warning)
            return
        self.__before += c

    def __print_warning(self, warning):
        print_len = self.__printed_warning
        if self.__printed_warning > 0:
            sys.stdout.write(char.cursor_up(1))
        else:
            console.increment_linecounter()
            print_len = self.__message_len + self.__text_len
        sys.stdout.write('\r' + strutil.ljust(warning, print_len) + '\n\r')
        self.__printed_warning = strutil.display_width(warning)


class Progress(object):
    __color = color.ColorSet(color.BOLD)

    def __init__(self, message):
        self.__timer = timer.Timer(self.__increment_progress, 1.00)
        self.__message = message
        self.__progress = 0
        self.__print_progress()
        self.__timer.start()

    def done(self):
        self.__complete(strutil.hilite('[DONE]', self.__color.green))

    def fail(self, ex=None):
        self.__complete(strutil.hilite('[FAIL]', self.__color.red))

    def notify(self, message):
        self.__timer.synchronized(lambda x: self.__notify(message))

    def __notify(self, message):
        plen = len(self.__message) + self.__progress + 2
        console.notify(strutil.ljust(message, plen))
        self.__print_progress()

    def __print_progress(self):
        dots = ''.ljust(self.__progress, '.')
        sys.stdout.write('\r%s %s' % (self.__message, dots))
        sys.stdout.flush()

    def __increment_progress(self):
        self.__progress += 1
        self.__print_progress()

    def __complete(self, resolution):
        self.__timer.cancel()
        self.__print_progress()
        sys.stdout.write(' %s %.3fs\n\r' % (
            resolution, self.__timer.duration()))
        sys.stdout.flush()
        console.increment_linecounter()
