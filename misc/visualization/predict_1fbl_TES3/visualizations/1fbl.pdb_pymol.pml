
from pymol import cmd,stored

set depth_cue, 1
set fog_start, 0.4

set_color b_col, [36,36,85]
set_color t_col, [10,10,10]
set bg_rgb_bottom, b_col
set bg_rgb_top, t_col      
set bg_gradient

set  spec_power  =  200
set  spec_refl   =  0

load "data/1fbl.pdb", protein
create ligands, protein and organic
select xlig, protein and organic
delete xlig

hide everything, all

color white, elem c
color bluewhite, protein
#show_as cartoon, protein
show surface, protein
#set transparency, 0.15

show sticks, ligands
set stick_color, magenta




# SAS points

load "data/1fbl.pdb_points.pdb.gz", points
hide nonbonded, points
show nb_spheres, points
set sphere_scale, 0.2, points
cmd.spectrum("b", "green_red", selection="points", minimum=0, maximum=0.7)


stored.list=[]
cmd.iterate("(resn STP)","stored.list.append(resi)")    # read info about residues STP
lastSTP=stored.list[-1] # get the index of the last residue
hide lines, resn STP

cmd.select("rest", "resn STP and resi 0")

for my_index in range(1,int(lastSTP)+1): cmd.select("pocket"+str(my_index), "resn STP and resi "+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.show("spheres","pocket"+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_scale","0.4","pocket"+str(my_index))
for my_index in range(1,int(lastSTP)+1): cmd.set("sphere_transparency","0.1","pocket"+str(my_index))



set_color pcol1 = [0.361,0.576,0.902]
select surf_pocket1, protein and id [963,990,991,992,993,994,1027,1033,1035,1036,1025,1026,664,667,670,671,676,971,679,958,959,960,961,962,938,942,943,972,661,934,929,988,989,1016,682,700,692,693,696,32,33,1105,1106,1111,1117,651,653,644,646,1112,1113,1118,1119,658,659,1122,1124,657] 
set surface_color,  pcol1, surf_pocket1 
set_color pcol2 = [0.329,0.278,0.702]
select surf_pocket2, protein and id [758,760,173,514,515,761,730,728,746,171,172,501,472,499,498,564,582,560,570,532,561] 
set surface_color,  pcol2, surf_pocket2 
set_color pcol3 = [0.698,0.361,0.902]
select surf_pocket3, protein and id [2107,2109,2144,2152,2091,2092,1991,2150,2151,2161,2163,2444,1890,1891,2426,2439,2459,2455,1910,2064,1908,1909,1926] 
set surface_color,  pcol3, surf_pocket3 
set_color pcol4 = [0.702,0.278,0.639]
select surf_pocket4, protein and id [1488,1490,1486,1495,1497,1513,1619,1620,2039,1754,1757,1750,1528,1756,1769,1489,1766,1917,1498,1499,1900,2030,2031,2034,2035,2038,1896,1897,1902,1899,1901,2017] 
set surface_color,  pcol4, surf_pocket4 
set_color pcol5 = [0.902,0.361,0.545]
select surf_pocket5, protein and id [2936,2937,2772,2924,2926,2927,2928,2925,2895,2896,2898,1637,2935,2938,2940,2674,2708,2686,2688,2705,2707,2770,2771,2939,2789,2881,2791,2792,2793] 
set surface_color,  pcol5, surf_pocket5 
set_color pcol6 = [0.702,0.353,0.278]
select surf_pocket6, protein and id [1482,1860,1861,2652,2653,1481,1491,1502,2666,2659,2663,2667,1492,1870,1874,2280,2281,2282,2283,2284,1857,1856,2263,2259,2656] 
set surface_color,  pcol6, surf_pocket6 
set_color pcol7 = [0.902,0.729,0.361]
select surf_pocket7, protein and id [2411,2503,2558,2559,2309,2312,2413,2330,2848,2822,2823,2847,2298,2297,2299,2329] 
set surface_color,  pcol7, surf_pocket7 
   

deselect

orient
