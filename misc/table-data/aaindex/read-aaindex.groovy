#!/bin/bash
//usr/bin/env groovy  -cp "../../../distro/bin/p2rank.jar:../../../distro/bin/lib/*"  "$0" $@; exit $?

// groovy  -cp "../../../distro/bin/p2rank.jar;../../../distro/bin/lib/guava-23.0.jar" read-aaindex.groovy  aaindex1.txt


package aaindex

import cz.siret.prank.domain.AA
import cz.siret.prank.features.tables.AAIndex1



def fname = args[0]

AAIndex1 aaindex = AAIndex1.parse(new File(fname).text)

println "indexId," + AA.values().join(",")

for (AAIndex1.Entry entry : aaindex.entries) {
    println entry.id + "," + AA.values().collect{ entry.values.get(it) }.join(",")
}








