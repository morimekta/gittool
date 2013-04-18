import sys, tty, termios

from utils import char


def interactive(read=lambda getkey: getkey(), default=char.EOF):
    """Get a single character or keystroke from the user.
    
    This function will wait indefinitely on user input."""
    old_settings = termios.tcgetattr(__stdin)
    try:
        tty.setraw(sys.stdin.fileno())
        return read(lambda: __getkey())
    finally:
        termios.tcsetattr(__stdin, termios.TCSAFLUSH, old_settings)
    return default


def non_interactive(execute):
    """Do something that is specifically non-interactive.
    
    i.e.: i = getch.non_interactive(lambda: os.system('my_command -p'))
    
    This can be run from from within an interactive mode area. Output from this
    method should follow the normal console terminal flow rules."""
    old_settings = termios.tcgetattr(__stdin)
    try:
        tty.setcbreak(sys.stdin.fileno())
        return execute()
    finally:
        termios.tcsetattr(__stdin, termios.TCSAFLUSH, old_settings)
    return None


def getch():
    """Get a single character or keystroke from the user.
    
    This function will wait indefinitely on user input."""
    old_settings = termios.tcgetattr(__stdin)
    try:
        tty.setraw(sys.stdin.fileno())
        return __getkey()
    finally:
        termios.tcsetattr(__stdin, termios.TCSAFLUSH, old_settings)
    return char.EOF


""" ------------------------------ PRIVATE --------------------------------- """


__mapped_keys = dict({
    '\r': '\n',
    char.ESC + 'OH': char.HOME,
    char.ESC + 'OF': char.END})
__stdin = sys.stdin.fileno()


def __getkey():
    key = sys.stdin.read(1)
    if key == char.ESC:
        key = sys.stdin.read(1)
        while len(key) < 5 and not char._is_escape(key):
            ch = sys.stdin.read(1)
            if ch == char.ESC:
                return char.ESC
            key += ch
        key = char.ESC + key
    elif key == char.DEL:
        return char.BACKSPACE
    elif ord(key) > 127:  # utf8 multibyte.
        key += sys.stdin.read(1)

    if __mapped_keys.has_key(key):
        return __mapped_keys[key]
    return key
