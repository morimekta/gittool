import re, os

from datetime import tzinfo
from datetime import timedelta
from datetime import datetime

from utils import shared

class TimeZone(tzinfo):
    def __init__(self, tz_hour, tz_min, name):
        self.tz_hour = tz_hour
        self.tz_min = tz_min
        self.name = name

    def __delta(self):
        if self.tz_hour < 0:
            # To make the minute offset count in the right direction.
            return timedelta(hours=self.tz_hour, minutes=-self.tz_min)
        return timedelta(hours=self.tz_hour, minutes=self.tz_min)


    def utcoffset(self, dt):
        return self.__delta()

    def dst(self, dt):
        return self.__delta()

    def tzname(self, dt):
        return self.name

    def __str__(self):
        return '%+02d:%02d' % (self.tz_hour, self.tz_min)


UTC = TimeZone(0, 0, 'UTC')


def zoneinfo(string):
    string = string.strip()
    off = __get_offset(string)
    if off == None:
        off = string
    off = __strip_gmt.sub('', off)
    hm = off.split(':')
    tz_hour = int(hm[0])
    tz_min = 0
    if tz_hour > 24:
        tz_min = tz_hour % 100
        tz_hour = int(tz_hour / 100)
    elif tz_hour < -24:
        tz_min = (-tz_hour) % 100
        tz_hour = int((tz_hour + tz_min) / 100)
    elif len(hm) > 1:
        tz_min = int(hm[1])

    return TimeZone(tz_hour, tz_min, string)

def parse_iso(string):
    dt = datetime.strptime(string[:19], '%Y-%m-%d %H:%M:%S')
    return dt.replace(tzinfo=zoneinfo(string[19:]))

def format_iso(dt):
    return dt.strftime('%Y-%m-%d %H:%M:%S %z')

def format_short(dt):
    now = datetime.now(dt.tzinfo)
    rel = now - dt

    if now.date() == dt.date() or rel.days == 0:
      return dt.strftime('%H:%M:%S')

    return dt.strftime('%Y-%m-%d')


""" ------------------------------- PRIVATE ------------------------------- """

__strip_gmt = re.compile(r'(GMT|UTC)\s*')
__offsetdata = None
__re_tzinfo = re.compile('(?P<name>.*)\s(?P<offset>[-+][0-9][0-9]?(:[03]0)?)')

def __get_offset(zone):
    global __offsetdata
    if __offsetdata == None:
        __offsetdata = dict()
        f = open(shared.path('tzdata.dict'))
        for l in f:
            m = __re_tzinfo.match(l.strip())
            if m != None:
                __offsetdata[m.group('name')] = m.group('offset')
        f.close()
    return __offsetdata.get(zone)
