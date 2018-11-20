




# standard
misc/test-scripts/benchmark.sh 20 "U48" "1 2 4 8"              "./prank.sh predict -c workdef u48.ds -l SPEEDTEST"
misc/test-scripts/benchmark.sh 10 "U48" "1 2 4 8 12 16 20 24"  "./prank.sh predict -c working u48.ds -l SPEEDTEST"
misc/test-scripts/benchmark.sh 5 "U48" "1 2 4 8 12 16"  "./prank.sh predict -c config/working fptrain.ds -l SPEEDTEST"
misc/test-scripts/benchmark.sh 3 "U48" "12"  "./prank.sh traineval -t chen11-fpocket.ds -e joined.ds   -c config/working -loop 1 -l SPEEDTEST"


misc/test-scripts/benchmark.sh 1 "dt198" "1" "./prank.sh predict -c workdef dt198.ds -l SPEEDTEST"



