#!/usr/bin/python

import os, sys, random, time

__script_path, __script_name = os.path.split(os.path.realpath(__file__))
__src_path = os.path.normpath(os.path.join(__script_path, '..'))

sys.path.insert(0, __src_path)

from utils import getch, char, interactive

def out(txt):
	sys.stdout.write(txt)

out("Test input: ")
l = getch.getch()

out(repr(l) + "\n")
