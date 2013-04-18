#!/usr/bin/python

import os, sys, random, time

__script_path, __script_name = os.path.split(os.path.realpath(__file__))
__src_path = os.path.normpath(os.path.join(__script_path, '..'))

sys.path.insert(0, __src_path)

from utils import char, color

def out(txt):
	sys.stdout.write(txt)

mods = []
for i in sys.argv[1:]:
	mods.append(int(i))

random.seed(time.time())

colors = range(30, 38) + [39]
random.shuffle(colors)

backgrounds = range(40, 48) + [49]
random.shuffle(backgrounds)

out('            ')
for bg in backgrounds:
	out(' %dm ' % bg)
out('\n     m  gYw ')
for bg in backgrounds:
	out('%s gYw ' % char.color(bg))

for i in mods:
	out('%s\n    %dm %s gYw ' % (char.color(0), i, char.color(i)))
	for bg in backgrounds:
		out('%s gYw ' % char.color(i, bg))

for col in colors:
	out('%s\n   %dm %s gYw ' % (char.color(0), col, char.color(col)))
	for bg in backgrounds:
		out('%s gYw ' % char.color(bg))
	for i in mods:
		out('%s\n %d;%dm %s gYw ' % (char.color(0), i, col, char.color(i, col)))
		for bg in backgrounds:
			out('%s gYw ' % char.color(i, bg))
out('%s\n\n' % char.color(0))

out('            ')
for bg in colors:
	out(' %dm ' % bg)
for col in backgrounds:
	out('%s\n   %dm %s gYw ' % (char.color(0), col, char.color(col)))
	for bg in colors:
		out('%s gYw ' % char.color(bg))
	for i in mods:
		out('%s\n %d;%dm %s gYw ' % (char.color(0), i, col, char.color(i, col)))
		for bg in colors:
			out('%s gYw ' % char.color(i, bg))
out('%s\n' % char.color(0))

sys.stdout.flush()
