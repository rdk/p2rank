


# 1o6u.pdb / 1o6u.cif

Has chain discontinuity in A (321 -> 326) and C (322 -> 332).

P2Rank 2.4.1 cuts the chains at first break
* in _residues.csv there are no residues after A_321 and C_322
* seems that rest of the atoms are considered non-protein internally

~~~pdb
REMARK 465 MISSING RESIDUES                                                     
REMARK 465 THE FOLLOWING RESIDUES WERE NOT LOCATED IN THE                       
REMARK 465 EXPERIMENT.

...

ATOM   2600  OXT LYS A 321      -8.657  30.373  61.568  1.00 42.33           O  
ATOM   2601  N   GLU A 326     -16.965  29.270  66.824  1.00 48.21           N  

...

ATOM   5791  OXT THR C 322      12.751  14.157  -6.216  1.00 51.08           O  
ATOM   5792  N   GLU C 332       9.550  11.736 -12.274  1.00 44.39           N  
~~~


