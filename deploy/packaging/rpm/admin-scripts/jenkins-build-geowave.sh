#!/bin/bash
#
# GeoWave Jenkins Build Script
#
# In the Execute Shell block before calling this script set the versions

cd $WORKSPACE/deploy

# Build each of the "fat jar" artifacts and rename to remove any version strings in the file name

mvn package -P geotools-container-singlejar $BUILD_ARGS "$@"
mv $WORKSPACE/deploy/target/*-geoserver-singlejar.jar $WORKSPACE/deploy/target/geowave-geoserver.jar

mvn package -P accumulo-container-singlejar $BUILD_ARGS "$@"
mv $WORKSPACE/deploy/target/*-accumulo-singlejar.jar $WORKSPACE/deploy/target/geowave-accumulo.jar

mvn package -P geowave-tools-singlejar $BUILD_ARGS "$@"
mv $WORKSPACE/deploy/target/*-tools.jar $WORKSPACE/deploy/target/geowave-tools.jar

# Build the jace artifacts (release, debug, source) and include geotools-vector ingest tool to support testing
mkdir -p $WORKSPACE/deploy/target/jace
mvn package -P generate-jace-proxies,linux-amd64-gcc-debug $BUILD_ARGS "$@"
mv $WORKSPACE/deploy/target/*-jace.jar $WORKSPACE/deploy/target/jace/geowave-jace.jar
mv $WORKSPACE/deploy/target/dependency/jace-core-runtime-*.jar $WORKSPACE/deploy/target/jace/jace-core-runtime.jar
cp $WORKSPACE/extensions/formats/geotools-vector/target/*-tools.jar $WORKSPACE/deploy/target/jace/geowave-ingest.jar
tar -czf $WORKSPACE/deploy/target/jace/jace-linux-amd64-debug.tar.gz \
    $WORKSPACE/deploy/target/jace/geowave-ingest.jar \
    $WORKSPACE/deploy/target/jace/geowave-jace.jar \
    $WORKSPACE/deploy/target/jace/jace-core-runtime.jar \
    $WORKSPACE/deploy/target/dependency/jace/libjace.so \
    -C $WORKSPACE/deploy/target/dependency/jace/include

mvn package -P generate-jace-proxies,linux-amd64-gcc-release $BUILD_ARGS "$@"
tar -czf $WORKSPACE/deploy/target/jace/jace-linux-amd64-release.tar.gz \
    $WORKSPACE/deploy/target/jace/geowave-ingest.jar \
    $WORKSPACE/deploy/target/jace/geowave-jace.jar \
    $WORKSPACE/deploy/target/jace/jace-core-runtime.jar \
    $WORKSPACE/deploy/target/dependency/jace/libjace.so \
    -C $WORKSPACE/deploy/target/dependency/jace/include

tar -czf $WORKSPACE/deploy/target/jace/jace-source.tar.gz \
    $WORKSPACE/deploy/target/jace/geowave-ingest.jar \
    $WORKSPACE/deploy/target/jace/geowave-jace.jar \
    $WORKSPACE/deploy/target/jace/jace-core-runtime.jar \
    $WORKSPACE/deploy/target/dependency/jace/CMakeLists.txt \
    -C $WORKSPACE/deploy/target/dependency/jace/source \
    -C $WORKSPACE/deploy/target/dependency/jace/include

# Build and archive HTML/PDF docs
cd $WORKSPACE/
mvn javadoc:aggregate
mvn -P docs -pl docs install
tar -czf $WORKSPACE/target/site.tar.gz -C $WORKSPACE/target site

# Build and archive the man pages
mkdir -p $WORKSPACE/docs/target/{asciidoc,manpages}
cp -fR $WORKSPACE/docs/content/manpages/* $WORKSPACE/docs/target/asciidoc
find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec sed -i "s|//:||" {} \;
find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec a2x -d manpage -f manpage {} -D $WORKSPACE/docs/target/manpages \;
tar -czf $WORKSPACE/docs/target/manpages.tar.gz -C $WORKSPACE/docs/target/manpages/ .

## Copy over the puppet scripts
tar -czf $WORKSPACE/deploy/target/puppet-scripts.tar.gz -C $WORKSPACE/deploy/packaging/puppet geowave
