import os

def path(file):
    return os.path.normpath(os.path.join(__shared, file))

## --- private ---

__sp, __sn = os.path.split(os.path.realpath(__file__))
__shared = os.path.normpath(os.path.join(__sp, '../share'))
