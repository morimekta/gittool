#!/usr/bin/python
import os, sys

__script_path, __script_name = os.path.split(os.path.realpath(__file__))

__shared_path = os.path.normpath(os.path.join(__script_path, '../../share'))
__src_path    = os.path.normpath(os.path.join(__script_path, '../src'))
#__src_path    = os.path.normpath(os.path.join(__script_path, '../lib/gt.par'))

os.environ['SHARED'] = __shared_path
sys.path.insert(0, __src_path)

from gittool import gtmain
gtmain.main(sys.argv)
