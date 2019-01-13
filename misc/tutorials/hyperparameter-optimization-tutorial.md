
# Hyperparameter optimization

## Spearmint



https://github.com/HIPS/Spearmint/tree/ffbab6653ae785c9acdcf2abb01c63127be40c2f

## Install Spearmint on ubuntu
```sh
# Spearmint uses Python 2.7        
sudo apt install -y mongodb python python-pip
sudo pip install --upgrade pip
sudo pip install numpy scipy pymongo weave
# git clone https://github.com/HIPS/Spearmint.git  # Spearmint home repo
git clone https://github.com/rdk/Spearmint.git     # fork fixing scipy.weave problem (weave-fix branch)
sudo pip install -e Spearmint
```


## Run optimization experiment

```sh
prank hopt -t <dataset>               -<param1> '(<min>,<max>)'     # crossvalidation
prank hopt -t <dataset> -e <dataset>  -<param1> '(<min>,<max>)'

```

Example:
```sh
pkill python; sudo pkill mongo  # prepare clean slate (careful, your other programs might die too)
./prank.sh hopt -c working -l TREES_w -out_subdir HOPT \
    -t chen11-fpocket.ds -e joined.ds \
    -loop 1 -log_level DEBUG -log_to_console 1 \
    -ploop_delete_runs 0 \
    -hopt_spearmint_dir '/home/rdk/proj/OTHERS/Spearmint/spearmint' \
    -rf_trees '(10,200)' -rf_depth '(2,14)' -rf_features '(2,30)'   
```


