



# Spearmint

https://github.com/HIPS/Spearmint/tree/ffbab6653ae785c9acdcf2abb01c63127be40c2f

## Install Spearmint on ubuntu
```sh
sudo apt install -y mongodb python 
sudo pip install --upgrade pip
sudo pip install numpy scipy pymongo
git clone https://github.com/HIPS/Spearmint.git
sudo pip install -e Spearmint
```



Example:
```sh
./prank.sh hopt -c working -l TREES_w -out_subdir HOPT \
    -t chen11-fpocket.ds -e joined.ds \
    -loop 1 -log_level DEBUG -log_to_console 1 \
    -ploop_delete_runs 0 \
    -hopt_spearmint_dir '/home/rdk/proj/OTHERS/Spearmint/spearmint' \
    -rf_trees '(10,200)' -rf_depth '(2,14)' -rf_features '(2,30)'   
```


