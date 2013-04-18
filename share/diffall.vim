" Vim script to be used for gt diff.
" Opens a tab for each pair of files.
"
" USAGE         "gvim '+so diffall.vim' a1 a2 b1 b2 ..."

function FnameEscape(str)
  return escape(a:str, " \t\n*?[{`$\\%#'\"|!<")
endfunction

set lazyredraw
set splitright  " put second version right of the first version ("gvim '+so diffall.vim' a b" will show b to the right).

set columns=190

let s:idx = 0
while s:idx < argc()
  if argv(s:idx) != ':'
    if s:idx >= 2
      tabnew
    endif
    exe "silent edit " . FnameEscape(argv(s:idx))
    let s:idx += 1
    exe "silent vertical diffsplit " . FnameEscape(argv(s:idx))
  endif
  let s:idx += 1
endwhile

" Go to first tab page
tabrewind

" redraw now
set nolazyredraw
redraw
