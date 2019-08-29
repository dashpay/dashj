#!/usr/bin/env bash

# git reset && 	git checkout . && git clean -fd

for C in sed rename; do
  command -v $C || { echo "$C needs to be installed, aborting"; exit 1; }
done

set -x
shopt -s globstar


rename -v 's/org\/bitcoinj/org\/dashj/' **/org/bitcoinj
rename -v 's/org\/bitcoin/org\/dash/' **/org/bitcoin
rename 's/org\.bitcoin\./org\.darkcoin\./' **
git add .
git commit -m "rename bitcoinj package to dashj"

sed -i 's/org\.bitcoinj\./org\.dashj\./g' **/*.java **/*.proto
sed -i 's/org\.bitcoin\([.;]\)/org\.dash\1/g' **/*.java
git add .
git commit -m "use bitcoinj package"

D=`date +%Y%m%d`
CURVERSION=`grep '<artifactId>dashj-parent</artifactId>' -A1 pom.xml  | tail -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/'`
sed -i '/<module>wallettemplate<\/module>/d' pom.xml
sed -i "s/<version>$CURVERSION<\/version>/<version>$CURVERSION-$D<\/version>/" {,**/}pom.xml
git add .
git commit -m "bump GB version"

# GB patch - dpub
git remote add gb git@github.com:GENERALBYTESCOM/dashj.git
git fetch gb
git cherry-pick 53827dbf2e8bdf0f5deb4d09ee1e9ec851a6eb10