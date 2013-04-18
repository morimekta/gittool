GIT Tools
=========

gittool is a small collection og helper programs to streamline my git usage.
The main utility is `gt`, which wraps some git commands in interactive
interface, and makes a bit more powerful diff utility using gvim.

## gt

The main git-tool cli.

```
usage: gt <command> [<flags>*] [--] [<file>*]
       gt help [command]

Commands available are:
  branch     - Change branch interactively.
  diff       - Shows file differences.
  newcl      - Select files from current branch to base new branch on.
  status     - Show the current branch status.

Short hands for some well used commands:
  branch     - gt b   - Change branch interactively.
  status     - gt st  - Show the current branch status.
```

## gts

A search wrapper that shows results column formatted and color coded.

## gtre

Wrapper around tree that filters out git ignored files. E.g.:

```
# gtre
.
├── BUILD
├── gittool
│   ├── branches.py
│   ├── BUILD
│   ├── diffhelper.py
│   ├── difftool.py
│   ├── diffutil.py
│   ├── gitcmd.py
│   ├── gitconfig.py
│   ├── gitcore.py
│   ├── gitdiff.py
│   ├── gitignore.py
│   ├── gitutil.py
│   └── __init__.py
├── gt.py
├── gtre.py
├── gts.py
├── __init__.py
├── README.md
├── share
│   ├── diffall.vim
│   └── tzdata.dict
├── tests
│   ├── BUILD
│   ├── colors_test.py
│   └── interactive_test.py
├── utils
│   ├── BUILD
│   ├── char.py
│   ├── char_test.py
│   ├── cmd.py
│   ├── color.py
│   ├── color_test.py
│   ├── console.py
│   ├── fileutil.py
│   ├── getch.py
│   ├── __init__.py
│   ├── interactive.py
│   ├── selection.py
│   ├── shared.py
│   ├── strutil.py
│   ├── timer.py
│   └── timeutil.py
└── WORKSPACE

4 directories, 40 files
```
