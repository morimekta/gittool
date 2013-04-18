import os, sys, subprocess

from utils import shared

def show_diff(file_a, file_b, write=False):
    cmd = []
    if (sys.platform == 'darwin'):
        # We're in MacOS, use opendiff command.
        cmd.append('mvim')
    else:
        # Assume linux/GTK+ in all other cases.
        cmd.append('gvim')
    cmd.append('-d')
    cmd.append('-f')
    if not write:
        cmd.append('-R')

    cmd.append(file_a)
    cmd.append(file_b)
        
    p = subprocess.Popen(
            cmd, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    p.wait()
    for l in p.stderr:
        pass
    for l in p.stdout:
        pass


def allow_diffall():
    """Whether the platform supports diffall.
    """
    return True
    # return sys.platform != 'darwin'

def show_diffall(all_files, write):
    cmd = []
    if (sys.platform == 'darwin'):
        # We're in MacOS, use opendiff command.
        cmd.append('mvim')
    else:
        # Assume linux/GTK+ in all other cases.
        cmd.append('gvim')
    cmd.append('-f')
    if not write:
        cmd.append('-R')
    _path, _name = os.path.split(os.path.realpath(__file__))
    cmd.append('+so %s' % shared.path('diffall.vim'))

    for fil in all_files:
        cmd.append(fil)

    p = subprocess.Popen(
            cmd, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    p.wait()
    for l in p.stderr:
        pass
    for l in p.stdout:
        pass
