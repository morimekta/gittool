import os

from utils import char, color

class FileTree(object):
    ENTRY       = ' %s%s' % (char.Table.T1101, char.Table.T0011)
    LAST_ENTRY  = ' %s%s' % (char.Table.T1001, char.Table.T0011)

    PREFIX      = ' %s ' % (char.Table.T1100)
    LAST_PREFIX = '   '

    FILE        = char.color(color.DEFAULT)
    DIR         = char.color(color.BLUE)

    def __init__(self):
        
