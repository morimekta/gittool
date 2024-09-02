GIT Tools
=========

[![Morimekta](https://img.shields.io/static/v1?label=morimekta.net&message=gittool&color=informational)](https://morimekta.net/gittool/)
[![License](https://img.shields.io/static/v1?label=license&message=apache%202.0&color=informational)](https://apache.org/licenses/LICENSE-2.0)

gittool is a small collection og helper programs to streamline my git usage.
The main utility is `gt`, which wraps some git commands in interactive
interface, and makes a bit more powerful diff utility using gvim.

```
Extra git tools by morimekta - ${version}
Usage: gt [-hV] [--git_repository REPOSITORY] [--verbose] cmd [...]

 --git_repository REPOSITORY : The git repository root directory
 --help (-h, -?)             : Show help
 --version (-V)              : Show program version
 --verbose                   : Show verbose exceptions
 cmd                         : Command to act on git repo with

Available sub-commands:

 help   : Show help
 branch : Change branch
 status : Review branch status
 diff   : Diff changes
```
