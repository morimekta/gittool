from utils import char


"""Text colors."""
CLEAR      = 0   # CLEAR clears all color, bg and mod state!
DEFAULT    = 39  # Default foreground.
BLACK      = 30
RED        = 31
GREEN      = 32
YELLOW     = 33
BLUE       = 34
MAGENTA    = 35
CYAN       = 36
WHITE      = 37


"""Background colors."""
BG_DEFAULT = 49  # Default background.
BG_BLACK   = 40
BG_RED     = 41
BG_GREEN   = 42
BG_YELLOW  = 43
BG_BLUE    = 44
BG_MAGENTA = 45
BG_CYAN    = 46
BG_WHITE   = 47


"""Color modifiers."""
BOLD       = 1
DIM        = 2
UNDERLINE  = 4
STROKE     = 9  # Also called 'overline'.
INVERT     = 7
HIDDEN     = 8  # Foreground color becomes same as background.

# NOT_* values will not add, only negate it's counter-part if present
NOT_BOLD      = 20 + BOLD
NOT_DIM       = 20 + DIM
NOT_UNDERLINE = 20 + UNDERLINE
NOT_STROKE    = 20 + STROKE
NOT_INVERT    = 20 + INVERT
NOT_HIDDEN    = 20 + HIDDEN


class Color(char.Char):
    def __init__(self, *codes):
        self.modset = dict()
        self.color  = None
        self.bg     = None
        for code in codes:
            if code == 0:
                self.color = 0
                self.bg    = None
                self.mods  = []
                break
            elif code in [ 1, 2, 4, 7, 8, 9 ]:
                self.modset[code] = True
            elif code in [ 21, 22, 24, 27, 28, 29 ]:
                self.__rm_mod(code - 20)
            elif 30 <= code and code < 40 and code != 38:
                self.color = code
            elif 40 <= code and code < 50 and code != 48:
                self.bg = code
            else:
                raise Exception('Color code ' + code + ' not valid.')
        super(Color, self).__init__(char.color(*self.get_codes()))

    def get_codes(self):
        """Get the color code numbers for the color definition."""
        codes = self.modset.keys()[:]
        if self.color != None:
            codes.append(self.color)
        if self.bg != None:
            codes.append(self.bg)
        codes.sort()
        return codes

    def mod(self, *mod_codes):
        """Make a modified version of the color with given modifier codes."""
        codes = self.get_codes()
        for code in mod_codes:
            codes.append(code)
        return Color(*codes)

    def diff(self, color):
        """Returns a list of true changes to the color given the new color."""
        codes = []
        if self.color != color.color:
            codes.append(color.color)
        if self.bg != color.bg:
            if color.bg != None:
                codes.append(color.bg)
            else:
                codes.append(BG_DEFAULT)

        it_only = set(color.modset.keys()) - set(self.modset.keys())
        for code in it_only:
            codes.append(code)

        me_only = set(self.modset.keys()) - set(color.modset.keys())
        for code in me_only:
            codes.append(code + 20)

        codes.sort()
        return codes

    def diff_codes(self, *codes):
        """Returns a list of true changes to the color given set of color codes."""
        color = self.mod(*codes)
        return self.diff(color)

    def __rm_mod(self, mod):
        if self.modset.has_key(mod):
            del self.modset[mod]

NONE = char.color(CLEAR)

class ColorSet(Color):
    def __init__(self, *codes):
        super(ColorSet, self).__init__(*codes + (DEFAULT, ))

        self.black   = Color(*codes + (BLACK, ))
        self.red     = Color(*codes + (RED, ))
        self.green   = Color(*codes + (GREEN, ))
        self.yellow  = Color(*codes + (YELLOW, ))
        self.blue    = Color(*codes + (BLUE, ))
        self.magenta = Color(*codes + (MAGENTA, ))
        self.cyan    = Color(*codes + (CYAN, ))
        self.white   = Color(*codes + (WHITE, ))

    def mod(self, *mod_codes):
        """Make a modified version of the color with given modifier codes."""
        codes = self.get_codes()
        for code in mod_codes:
            codes.append(code)
        return ColorSet(*codes)
