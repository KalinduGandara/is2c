#!/usr/bin/env bash
# Prints per-class results and totals from the last surefire run.
cd "$(dirname "$0")"
grep -h -oE 'testsuite [^>]*name="[^"]+"[^>]*' target/surefire-reports/TEST-*.xml 2>/dev/null \
  | sed -E 's/.*name="([^"]+)".*tests="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*failures="([0-9]+)".*/\2 \3 \5 \1/' \
  | awk '{t+=$1; e+=$2; f+=$3; if ($2>0 || $3>0) print "FAILED:", $4, "errors=" $2, "failures=" $3} END {print "TOTAL tests=" t, "errors=" e, "failures=" f}'
