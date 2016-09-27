#!/bin/bash
//usr/bin/env groovy  -cp "../../../distro/bin/pocket-rank.jar:../../../distro/bin/lib/*"  "$0" $@; exit $?


package aaindex

import rdk.pockets.domain.AA
import rdk.pockets.tools.aaindex.AAIndex1



def fname = args[0]

AAIndex1 aaindex = AAIndex1.parse(new File(fname).text)

println "indexId," + AA.values().join(",")

for (AAIndex1.Entry entry : aaindex.entries) {
    println entry.id + "," + AA.values().collect{ entry.values.get(it) }.join(",")
}








