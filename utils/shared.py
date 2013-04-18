import os

def path(file):
    share_path = os.environ['SHARED']
    return os.path.normpath(os.path.join(share_path, file))
