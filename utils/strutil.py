import re

from utils import char
from utils import color


def display_width(string, startcol=0, tabstop=4):
    """Find the displayed width of the string if printed.

    - string {str} The string to find the width of.
    - ctartcol  {int} The first column of the string, affect the alignment of
                      the columns.
    - tabstop   {int} The with of each tab alignment.
    """
    pos = 0
    length = 0
    while pos < len(string):
        ch = char.Char(string[pos:])
        if str(ch) == '\t':
            length += tabstop - (length % tabstop) 
        else:
            length += ch.display_width()
        pos += len(ch)
    return length


def expandtabs(string, startcol=0, tabstop=4, tabchar=' '):
    """Expand tabs in the given string.

    Each tab character '\\t' will be replaced with the "tabchar" and spaces
    until it reaches the next 'tabwidth' column. In case of uneven start,
    the startcolumn can also be specified, which will result in the
    alignment of the first tab-stop.

    - string    {str} The string to expand.
    - ctartcol  {int} The first column of the string, affect the alignment of
                      the columns.
    - tabstop   {int} The with of each tab alignment.
    - tabchar   {str} Character to represent the tab, will be padded with
                      spaces to fill the width of the tab expantion.
    return {str} The expanded string
    """
    pass


def trimwidth(string, width, widen=False):
    """Trim the width of the string to maximum the given length.

    - string {str}     The string to trim.
    - width  {num}     The width to trim to.
    - widen  {boolean} If the string is shorter, widen it instead up to the
                       given width.
    """
    width = int(width)
    owidth = display_width(string)

    if owidth == width:
        return string

    if owidth < width:
        if widen:
            return ljust(string, width)
        return string

    # Calculate the position just after the last printed char.
    pos = 0
    length = 0
    while pos < len(string):
        ch = char.Char(string[pos:])
        length += ch.display_width(length)
        pos += len(ch)
        if length >= width:
            # The right length (exactly).
            break
    before = string[:pos]
    # Add a color neutralizer at the end.
    return before + char.color(0)


def ljust(string, width, fill=' '):
    """Left justify the string.

    Uses the display width of the string instead of byte length.

    - string {str} The string to justify.
    - width  {int} Prefferred width of string.
    - fill   {str=' '} Char to fill.
    return {str} The justified string.
    """
    # Non-int width may result in a never-ending loop.
    width = int(width)
    owidth = display_width(string)

    if (owidth < width):
        add = width - owidth
        return string + ''.ljust(add, fill)
    return string


def rjust(string, width, fill=' '):
    """Right justify the string.

    Uses the display width of the string instead of byte length.

    - string {str} The string to justify.
    - width  {int} Prefferred width of string.
    - fill   {str=' '} Char to fill.
    return {str} The justified string.
    """
    width = int(width)
    owidth = display_width(string)

    if (owidth < width):
        add = width - owidth
        return ''.ljust(add, fill) + string
    return string


def str2hex(string):
    """Display a string as hex.
    
    Display the given string (assumed to be bytecode) as a continous
    hexadecimal string code.

    - string {str} The string to parse.
    """
    return ''.join(map(lambda x: "%02x" % ord(x), string))


def hilite(string, col, default=color.NONE):
    """Colorize the given text based on the color in color.
    
    - string {string} String to hilite.
    - col {Color|string} The color to hilite with.
    - default {Color|string} The default color for ending the hilite.
    return {string} The hilighted string.
    """
    # If the text contains CLEAR, replace that with rather setting the
    # hilite color. Also clear old color first to avoid color bleeding.
    string = __replace_endcolor.sub(color.NONE + str(col), string)
    return str(col) + string + str(default)


""" ------------------------------ PRIVATE --------------------------------- """


__replace_endcolor = re.compile(color.NONE.replace('[', '\\['))
