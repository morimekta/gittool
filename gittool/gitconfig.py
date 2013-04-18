import re, os

def master():
    global __master
    if __master is None:
        m = get_value('default.branch')
        if m is None:
            __master = 'master'
        __master = m
    return __master

def set_value(key, value, toGlobal=False):
    cmd = ['git', 'config']
    if toGlobal:
        cmd.append('--global')
    cmd.append(key)
    cmd.append(value)

    f = os.popen(' '.join(cmd))
    for line in f:
        if len(line.strip()) > 0:
            return False
    return True

def get_value(key):
    cmd = 'git config --get %s' % key
    f = os.popen(cmd)
    for line in f:
        line = line.strip()
        if len(line) > 0:
            return line
    return None

def unset_value(key):
    cmd = 'git config --unset %s' % key
    f = os.popen(cmd)

    for line in f:
        if len(line.strip()) > 0:
            return False
    return True

def entries(prefix=None):
    res = dict()
    cmd = 'git config --list'
    f = os.popen(cmd)
    for line in f:
        m = __re_config_line.match(line.strip())
        if m != None:
            key = m.group('key')
            value = m.group('value')
            if (prefix != None):
                if len(key) >= len(prefix) and key[0:len(prefix)] == prefix:
                    res[key] = value
            else:
                res[key] = value
    return res

def get_repositories():
    res = dict()
    cmd = 'git config --get-regex remote\..*\.url'
    f = os.popen(cmd)
    for line in f:
        line = line.strip()
        m = __re_remote_line.match(line)
        if m != None:
            name = m.group('name')
            url = m.group('url')
            res[name] = url
    return res

def set_repository(name, url):
    if url == None:
        unset_value('remote.%s.fetch' % name)
        unset_value('remote.%s.url'   % name)
        return True
    if set_value('remote.%s.fetch' % name, '+refs/heads/*:refs/remotes/%s/*' % name):
        return set_value('remote.%s.url'   % name, url)
    return False

def get_tracking(local):
    origin = get_value('branch.%s.remote' % local)
    merge = get_value('branch.%s.merge' % local)

    if merge != None:
        m = __re_merge_branch.match(merge)
        if m != None:
            branch = m.group('branch')
            return '%s/%s' % (origin, branch)
    return None

def get_all_tracking():
    e = entries(prefix='branch.')
    res = dict()
    for key in e.keys():
        if key[-7:] == '.remote':
            branch = key.split('.')[1]
            tracking = get_tracking(branch)
            if tracking != None:
                res[branch] = tracking
    return res

def set_tracking(local, remote=None):
    if remote == None:
        unset_value('branch.%s.remote' % local)
        unset_value('branch.%s.merge'  % local)
        return True

    remote_origin, remote_branch = remote.split('/', 1)

    if remote_branch == None:
        remote_branch = local_branch
    
    if set_value('branch.%s.remote' % local, remote_origin):
        return set_value('branch.%s.merge'  % local, 'refs/heads/%s' % remote_branch)
    return False

def get_diffbase(branch, allow_tracking=True, allow_master=True):
    diffbase = get_value("branch.%s.diffbase" % branch)
    if diffbase is None and allow_tracking:
        diffbase = get_tracking(branch)
    if diffbase is None and allow_master:
        diffbase = master()
    return diffbase

def set_diffbase(branch, diffbase):
    if diffbase == None:
        unset_value("branch.%s.diffbase" % branch)
        return
    set_value("branch.%s.diffbase" % branch, diffbase)


""" -- PRIVATE -- """

__re_config_line = re.compile('^(?P<key>[^=]*)=(?P<value>.*)$')
__re_remote_line = re.compile('^remote\.(?P<name>[^.]*)\.url (?P<url>.*)$')
__re_merge_branch = re.compile('refs/heads/(?P<branch>.*)')
__master = None

