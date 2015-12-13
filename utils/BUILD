py_library(
    name = 'char',
    srcs = ['char.py'],
    visibility = ['//visibility:public'],
)

py_test(
    name = 'char_test',
    srcs = ['char_test.py'],
    deps = [
        ':char',
    ],
    size = 'small',
)

py_library(
    name = 'cmd',
    srcs = ['cmd.py'],
    deps = [
        ':interactive',
        ':console',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'color',
    srcs = ['color.py'],
    deps = [
        ':char'
    ],
    visibility = ['//visibility:public'],
)

py_test(
    name = 'color_test',
    srcs = ['color_test.py'],
    deps = [
        ':color',
    ],
    size = 'small',
)

py_library(
    name = 'console',
    srcs = ['console.py'],
    deps = [
        ':char',
        ':color',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'fileutil',
    srcs = ['fileutil.py'],
    deps = [
        ':char',
        ':color',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'getch',
    srcs = ['getch.py'],
    deps = [
        ':char'
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'interactive',
    srcs = ['interactive.py'],
    deps = [
        ':char',
        ':color',
        ':console',
        ':getch',
        ':strutil',
        ':timer',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'selection',
    srcs = ['selection.py'],
    deps = [
        ':char',
        ':color',
        ':console',
        ':getch',
        ':strutil',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'shared',
    srcs = ['shared.py'],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'strutil',
    srcs = ['strutil.py'],
    deps = [
        ':char',
        ':color',
    ],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'timer',
    srcs = ['timer.py'],
    visibility = ['//visibility:public'],
)

py_library(
    name = 'timeutil',
    srcs = ['timeutil.py'],
    deps = [
        ':shared',
    ],
    data = [
        '//share:tzdata.dict'
    ],
    visibility = ['//visibility:public'],
)
