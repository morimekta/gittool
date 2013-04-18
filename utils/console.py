import sys, os

from utils import char
from utils import color


# The current notify function. Per default this is orig_notify (make newline
# *after* the message).
def notify(message):
    """Print a message on screen

    E.g. notify('Jeez...')

    - message  {str} Message to show.
    """
    __notify(message)


def info(message):
    """Print a simple info message.

    E.g. info('O maan!')

    - message  {str} Message to show.
    """
    notify('%s[INFO]%s %s' % (
        char.color(color.GREEN), char.color(0), message))


def warn(message):
    """Print a simple warning.

    E.g. warn('O s**t!')

    - message  {str} Message to show.
    """
    notify('%s[WARN]%s %s' % (
        char.color(color.YELLOW, color.BOLD), char.color(0), message))


def error(message):
    """Print a simple error message.

    E.g. error('WTF!')

    - message  {str} Message to show.
    """
    notify('%s[ERROR]%s %s' % (
        char.color(color.RED, color.BOLD), char.color(0), message))


def terminal_size():
    """Get the terminal size.

    return {(int, int)} Rows, Columns.
    """
    rows, cols = os.popen('stty size', 'r').read().split()
    return (int(rows), int(cols))


def reset_linecounter():
    """Reset line-counter and rerurn numver of lines written since last reset
    (or program start).

    return {integer} Number of lines written.
    """
    global __line_count
    ret = __line_count
    __line_count = 0
    return ret


def increment_linecounter():
    """Increment the line-counter.
    
    Call this function if a method adds lines without using the console
    notifications, but may be need line-counting.
    """
    global __line_count
    __line_count += 1


def replace_notify(in_between, notify=None):
    """Replace the notify function to alternate model.
    
    This replaces the notify(...) function from the standard (line-break after
    message), to an alternative model (line-break before message). This can be
    used to get seemless notifications while in an interactive mode.

    - in_between {Function} Function call that while this is ongoing, the
                            alternate notify(...) function is used.
    - notify {Function(string)} Optional alternative to notify.

    return {*} The result of in_between()
    """
    global __alt_mode, __notify

    if notify == None:
        notify = __ln_before_notify
    orig_notify = __notify
    __notify = notify

    __alt_mode = True
    try:
        return in_between()
    finally:
        __notify = orig_notify
        __alt_mode = False


def alt_mode():
    """Returns whether the console is in 'alternate notify(...) mode'."""
    global __alt_mode
    return __alt_mode


""" ------------------------------- PRIVATE ------------------------------- """


# Number of lines added.
__line_count = 0


def __ln_after_notify(message):
    global __line_count
    sys.stdout.write('\r%s\r\n' % message)
    sys.stdout.flush()
    __line_count += 1


def __ln_before_notify(message):
    global __line_count
    sys.stdout.write('\r\n%s' % message)
    sys.stdout.flush()
    __line_count += 1


# Alt-mode is the situation where the default is to keep the cursor at the end
# of 'current line', and not to make newline until 
__notify = __ln_after_notify
__alt_mode = False
