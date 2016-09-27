#!/bin/bash

groovy extract-atomtable.groovy raw.valids.csv > list.raw.valids.csv
groovy extract-atomtable.groovy raw.invalids.csv > list.raw.invalids.csv
groovy extract-atomtable.groovy 5sasa.valids.csv > list.5sasa.valids.csv
groovy extract-atomtable.groovy 5sasa.invalids.csv > list.5sasa.invalids.csv