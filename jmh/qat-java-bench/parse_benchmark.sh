#!/usr/bin/env bash

# TAB=$'\t'
TAB=,
echo "Type${TAB}Threads${TAB}Throughput (MB/s)${TAB}Compression Ratio"
for FILE in "$@"; do
  THREADS="$(echo "$FILE" | grep -o '_[0-9]*\.txt' | grep -o '[0-9]*')"
  N="$(grep 'ratio: [^ ]*' "$FILE" | wc -l)"
  for I in $(seq 1 "$N"); do
    TYPE="$(tail -n "$N" "$FILE" | head -n "$I" | tail -n 1 | cut -d' ' -f1)"
    TPUT="$(tail -n "$N" "$FILE" | head -n "$I" | tail -n 1 | grep -o '^[^ ]* *[^ ]*' | grep -o '[^ ]*$')"
    RATIO="$(grep -o 'ratio: [^ ]*' "$FILE" | cut -d' ' -f2 | head -n "$I" | tail -n 1)"
    echo "${TYPE}${TAB}${THREADS}${TAB}${TPUT}${TAB}${RATIO}"
  done
# done | sort -t"$TAB" -k2,2 -n -s | sort -t"$TAB" -s -k1,1
done
