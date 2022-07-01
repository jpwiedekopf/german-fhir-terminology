#!/bin/bash
jar="../build/libs/german-fhir-terminology-0.0.1-SNAPSHOT.jar"


basepath="/Users/joshua/No-Backup/german-fhir-ts" #TODO CHANGE ME
dir="OPS" #TODO CHANGE ME TOO

output="$basepath/output"
mkdir -p $output
logdir="$basepath/logs"
mkdir -p $logdir

files=`find $basepath/$dir -name "*.zip"`
pattern='(icd10gm|ops)([0-9]{2,4}).*.zip'
for f in $files; do
  if [[ $f =~ $pattern ]]; then
    version="${BASH_REMATCH[2]}"
    if [[ ${#version} -eq 2 ]]; then
      version="${version:0:1}.${version:1:1}"
    fi
    echo "$f - version: $version"
    java -jar $jar -o $output -r SGML -t $dir -i $f -v $version | tee "$logdir/$dir-$version.log"
  else
    echo "no match for $f" > /dev/stderr
    continue
  fi
done
