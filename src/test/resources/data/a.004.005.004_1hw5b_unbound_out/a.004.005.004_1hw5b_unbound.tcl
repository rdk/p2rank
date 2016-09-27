proc highlighting { colorId representation id selection } {
   set id [[atomselect $id $selection] molid]
   puts "highlighting $id"
   mol delrep 0 $id
   mol representation $representation
   mol color $colorId
   mol selection $selection
   mol addrep $id
}

set repr "Points 10"
highlighting ResID "Points 10" 0 "resname STP"
set id [[atomselect 0 "protein"] molid]
puts "highlighting $id"
mol representation "Lines"
mol material "Transparent"
mol color Element
mol selection "protein"
mol addrep $id
set id [[atomselect 0 "not protein and not resname STP"] molid]
puts "highlighting $id"
mol representation "Bonds"
mol color Element
mol selection "not protein and not resname STP"
mol addrep $id

mol new "../a.004.005.pdb"
mol selection "not protein and not water" 
                                 mol material "Opaque" 
                                 mol delrep 0 1 
                                 mol representation "Lines 10" 
                                 mol addrep 1 
                                 highlighting Element "NewCartoon" 1 "protein"
                                 mol representation "NewCartoon" 
                                 mol addrep $id 
                                 mol new "a.004.005_pockets.pqr"
                                 mol selection "all" 
                                 mol material "Glass1" 
                                 mol delrep 0 2 
                                 mol representation "VDW" 
                                 mol color ResID 2 
                                 mol addrep 2 
