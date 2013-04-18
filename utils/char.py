import re

def _utf8(unicode_str):
  return unicode_str.encode('utf-8')

"""Definitions of usable chars."""
ESC       = '\x1b'
BACKSPACE = '\x08'  # Backward delete.
DEL       = '\x7f'  # Same purpose as BACKSPACE

ABORT     = '\x03'  # <Ctrl-c> - End of text
EOF       = '\x04'  # <Ctrl-d> - End of transmission

BELL      = '\x07'  # Bell
CR        = '\x0d'  # Carriage Return.
LF        = '\x0a'  # Line Feed, '\n'.
TAB       = '\x09'  # Tab character.

UP        = ESC + '[A'
DOWN      = ESC + '[B'
LEFT      = ESC + '[D'
RIGHT     = ESC + '[C'

CTRL_UP   = ESC + '[1;5A'
CTRL_DOWN = ESC + '[1;5B'
CTRL_LEFT = ESC + '[1;5D'
CTRL_RIGHT= ESC + '[1;5C'

DPAD_MID  = ESC + '[E'

INSERT    = ESC + '[2~'
DELETE    = ESC + '[3~'
HOME      = ESC + '[1~'
END       = ESC + '[4~'
PAGE_UP   = ESC + '[5~'
PAGE_DOWN = ESC + '[6~'

F1        = ESC + 'OP'
F2        = ESC + 'OQ'
F3        = ESC + 'OR'
F4        = ESC + 'OS'
F5        = ESC + '[15~'
F6        = ESC + '[17~'
F7        = ESC + '[18~'
F8        = ESC + '[19~'
# F9-F12 are used by the terminal, so not accessible.

class Quote(object):
    APOS    = '\''
    QUOTE   = '"'
    LSQUO   = _utf8(u'\u2018')  # Left single quote.
    RSQUO   = _utf8(u'\u2019')  # Right single quote.
    BSQUO   = _utf8(u'\u201A')  # Single low-9 quote: ,Quote'.
    LDQUO   = _utf8(u'\u201C')  # Left double quote.
    RDQUO   = _utf8(u'\u201D')  # Right double quote.
    BDQUO   = _utf8(u'\u201E')  # Double low-9 quote: ,,Quote''.

    LSAQUO  = _utf8(u'\u2039')  # Left single angle quote: <
    RSAQUO  = _utf8(u'\u203A')  # Right single angle quote: >
    LAQUO   = _utf8(u'\u00AB')  # Left angle quote: <<
    RAQUO   = _utf8(u'\u00BB')  # French andle quote: >>

class Currency(object):
    CENT    = _utf8(u'\u00A2')  # 'c' with strike.
    POUND   = _utf8(u'\u00A3')  # Cursive 'L' with strike
    YEN     = _utf8(u'\u00A5')  # 'Y' with strike
    DOLLAR  = '$'
    EURO    = _utf8(u'\u00AC')  # Greek 'E' with double strike.

class Table(object):
    """Field name syntax is: T-Up-Down-Left-Right.

    Comments syntax is Left-Up/Down-Right:
    - means one horizontal line.
    ' means one line up.
    , means one line down.
    | means one full vertical line.
    = means two horizontal lines.
    " means two lines up.
    _ means two lines down.
    H means two full vertical lines.
    """
    # Single lines
    T0011  = _utf8(u'\u2500')  # --- Or -
    T0101  = _utf8(u'\u250C')  #  ,- Top left corner
    T0110  = _utf8(u'\u2510')  #  '- Bottom left corner
    T0111  = _utf8(u'\u252C')  # -,- T
    T1001  = _utf8(u'\u2514')  # -,  Top right corner
    T1010  = _utf8(u'\u2518')  # -'  Bottom right corner
    T1011  = _utf8(u'\u2534')  # -'- Up-side-down T
    T1100  = _utf8(u'\u2502')  #  |
    T1101  = _utf8(u'\u251C')  #  |-
    T1110  = _utf8(u'\u2524')  # -|
    T1111  = _utf8(u'\u253C')  # -|- Single line cross

    # Double lines
    T0022  = _utf8(u'\u2550')  # === Or =
    T0202  = _utf8(u'\u2554')  #  _= Top left corner
    T0220  = _utf8(u'\u255A')  #  "= Bottom left corner
    T0222  = _utf8(u'\u2566')  # =_= T
    T2002  = _utf8(u'\u2557')  # =_  Top right corner
    T2020  = _utf8(u'\u255D')  # ="  Bottom right corner
    T2022  = _utf8(u'\u2569')  # ="= Up-side-down T
    T2200  = _utf8(u'\u2551')  #  H
    T2202  = _utf8(u'\u2560')  #  H=
    T2220  = _utf8(u'\u2563')  # =H
    T2222  = _utf8(u'\u256C')  # =H= Double line cross

    # Mixed corners.
    T0102  = _utf8(u'\u2553')  #  ,=
    T0120  = _utf8(u'\u2555')  # =,
    T0201  = _utf8(u'\u2552')  #  _-
    T0210  = _utf8(u'\u2556')  # -_
    T1002  = _utf8(u'\u2558')  #  '=
    T1020  = _utf8(u'\u255B')  # ='
    T2001  = _utf8(u'\u2559')  #  "-
    T2010  = _utf8(u'\u255C')  # -"

    # Mixed T-crosses.
    T2011  = _utf8(u'\u2568')  # -"-
    T0211  = _utf8(u'\u2565')  # -_-
    T1120  = _utf8(u'\u2561')  # =|
    T1102  = _utf8(u'\u255F')  #  |=
    T1022  = _utf8(u'\u2567')  # ='=
    T0122  = _utf8(u'\u2564')  # =,=
    T2210  = _utf8(u'\u2562')  # -H
    T2201  = _utf8(u'\u255F')  #  H-

    # Mixed crosses.
    T1122  = _utf8(u'\u256A')  # =|=
    T2211  = _utf8(u'\u256B')  # -H-

class Fill(object):
    Q1     = _utf8(u'\u2591')  # 1/4 filled
    Q2     = _utf8(u'\u2592')  # 2/4 filled
    Q3     = _utf8(u'\u2593')  # 3/4 filled
    FULL   = _utf8(u'\u2588')  # Full block
    LEFT   = _utf8(u'\u258C')  # Left half
    RIGHT  = _utf8(u'\u2590')  # Right half
    TOP    = _utf8(u'\u2580')  # Top half
    BOTTOM = _utf8(u'\u2584')  # Bottom half

class Char(object):
    # Char class definition. Note: Only use this for e.g. calculating the real
    # length of strings etc.
    def __init__(self, string):
        if string[0] == ESC:
            i = 1
            while i < 20 and not (
                    is_escape(string[:i]) or is_color(string[:i])):
                i += 1
            self._char = string[:i]
        elif ord(string[0]) > 0x7F:
            self._char = string[:2]
        else:
            self._char = string[0]

    def __str__(self):
        return self._char

    def __int__(self):
        _ord = 0
        for c in self._char:
            if c == ESC:
                break
            _ord *= 0x100
            _ord += ord(c)
        return _ord

    def __len__(self):
        return len(self._char)

    def display_width(self, column=0):
        if (self._char[0] in [ ESC ] or
            self._char in [ BACKSPACE, ABORT, EOF, '\n', '\r', '' ]):
            return 0
        return 1


def is_escape(char):
    char = str(char)
    return len(char) > 1 and char[0] == ESC and _is_escape(char[1:])


def is_color(char):
    char = str(char)
    return len(char) > 3 and __color_char_pattern.search(char) != None


def color(*codes):
    """Make a color code char based on the code array."""
    return ESC + '[' + ';'.join(map(str, codes)) + 'm'


""" -------------------------- CURSOR CHARACTERS -------------------------- """
# NOTE: The responses to cursor functions can be written to console as simple
# escaped strings, but should never be stored and displayed.

def cursor_setpos(line, col=0):
    return ESC + '[' + str(line) + ';' + str(col) + 'H'

def cursor_up(num):
    return ESC + '[' + str(num) + 'A'

def cursor_down(num):
    return ESC + '[' + str(num) + 'B'

def cursor_left(num):
    return ESC + '[' + str(num) + 'D'

def cursor_right(num):
    return ESC + '[' + str(num) + 'C'

def cursor_erase():
    return ESC + '[K'

def cursor_save():
    return ESC + '[s'

def cursor_restore():
    return ESC + '[u'


""" ----------------------------- PROTECTED -------------------------------- """


def _is_escape(key):
    return (__re_alt.search(key) != None or
            __re_esc_1.search(key) != None or
            __re_esc_2.search(key) != None or
            __re_esc_3.search(key) != None)


""" ------------------------------ PRIVATE --------------------------------- """

__re_alt = re.compile(r'^([a-zA-NP-Z])')
__re_esc_1 = re.compile(r'^(O[A-Z])')
__re_esc_2 = re.compile(r'^(\[[;0-9]*[A-Z])')
__re_esc_3 = re.compile(r'^(\[[0-9][0-9]*~)')
__color_char_pattern = re.compile('^' + ESC + '\[[0-9][;0-9]*m$')
