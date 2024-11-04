#!/usr/bin/env bash

# color lines of log output

cat | awk -W interactive '

/ALL/     { print "\033[1;37m" $0 "\033[0m"; next; }
/FATAL/     { print "\033[1;31m" $0 "\033[0m"; next; }
/ERROR/     { print "\033[1;31m" $0 "\033[0m"; next; }
/WARN/     { print "\033[1;33m" $0 "\033[0m"; next; }
/INFO/     { print "\033[1;37m" $0 "\033[0m"; next; }
/DEBUG/     { print "\033[1;36m" $0 "\033[0m"; next; }
/TRACE/     { print "\033[1;32m" $0 "\033[0m"; next; }

{ print }'



          