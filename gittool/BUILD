py_library(
    name = 'branches',
    srcs = ['branches.py'],
    deps = [
        ':difftool',
        ':gitconfig',
        ':gitcore',
        ':gitdiff',
        ':gitutil',
        '//utils:char',
        '//utils:cmd',
        '//utils:color',
        '//utils:console',
        '//utils:interactive',
        '//utils:selection',
        '//utils:timeutil',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'diffhelper',
    srcs = ['diffhelper.py'],
    deps = [
        '//utils:shared',
    ],
    data = [
        '//share:diffall.vim'
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'difftool',
    srcs = ['difftool.py'],
    deps = [
        ':diffhelper',
        ':diffutil',
        '//utils:char',
        '//utils:color',
        '//utils:console',
        '//utils:interactive',
        '//utils:selection',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'diffutil',
    srcs = ['diffutil.py'],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitcmd',
    srcs = ['gitcmd.py'],
    deps = [
        ':difftool',
        ':gitconfig',
        ':gitcore',
        ':gitdiff',
        ':gitutil',
        '//utils:char',
        '//utils:cmd',
        '//utils:color',
        '//utils:console',
        '//utils:interactive',
        '//utils:timeutil',
        '//utils:strutil',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitconfig',
    srcs = ['gitconfig.py'],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitcore',
    srcs = ['gitcore.py'],
    deps = [
        ':diffutil',
        ':gitconfig',
        '//utils:timeutil',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitdiff',
    srcs = ['gitdiff.py'],
    deps = [
        ':difftool',
        ':gitcore',
        '//utils:char',
        '//utils:strutil',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitignore',
    srcs = ['gitignore.py'],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'gitutil',
    srcs = ['gitutil.py'],
    deps = [
        ':gitconfig',
        ':gitcore',
    ],
    visibility = ['//visibility:public'],
)
